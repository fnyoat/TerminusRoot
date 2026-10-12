#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/un.h>
#include <sys/wait.h>
#include <termios.h>

#define FRAME_DATA 0x00
#define FRAME_RESIZE 0x01
#define MAX_PAYLOAD 65536
#define IBUF_CAP (MAX_PAYLOAD + 16)

struct winsize g_ws;
volatile sig_atomic_t g_winch = 0;

static void on_winch(int sig) {
    (void) sig;
    g_winch = 1;
}

static int set_raw(int fd) {
    struct termios tio;
    if (tcgetattr(fd, &tio) < 0) return -1;
    cfmakeraw(&tio);
    return tcsetattr(fd, TCSANOW, &tio);
}

static int unix_connect(const char *path) {
    int s = socket(AF_UNIX, SOCK_STREAM, 0);
    if (s < 0) return -1;
    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    strncpy(addr.sun_path, path, sizeof(addr.sun_path) - 1);
    if (connect(s, (struct sockaddr *) &addr, sizeof(addr)) < 0) {
        close(s);
        return -1;
    }
    return s;
}

static int open_pty(int *master_out, char *slave_out, size_t slave_len) {
    int master = posix_openpt(O_RDWR | O_NOCTTY);
    if (master < 0) return -1;
    if (grantpt(master) < 0 || unlockpt(master) < 0) {
        close(master);
        return -1;
    }
    const char *name = ptsname(master);
    if (!name) {
        close(master);
        return -1;
    }
    strncpy(slave_out, name, slave_len - 1);
    slave_out[slave_len - 1] = '\0';
    *master_out = master;
    return 0;
}

static int write_all(int fd, const void *buf, size_t len) {
    const char *p = buf;
    while (len > 0) {
        ssize_t k = write(fd, p, len);
        if (k < 0) {
            if (errno == EINTR) continue;
            return -1;
        }
        p += k;
        len -= (size_t) k;
    }
    return 0;
}

static void set_win(int fd, int rows, int cols) {
    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_row = (unsigned short) rows;
    ws.ws_col = (unsigned short) cols;
    ioctl(fd, TIOCSWINSZ, &ws);
}

static void try_escape_cgroup(void) {
    if (geteuid() != 0) return;
    int fd = open("/sys/fs/cgroup/cgroup.procs", O_WRONLY);
    if (fd < 0) return;
    char buf[32];
    int n = snprintf(buf, sizeof(buf), "%d\n", (int) getpid());
    if (n > 0) {
        ssize_t ignored = write(fd, buf, (size_t) n);
        (void) ignored;
    }
    close(fd);
}

static int cmd_daemon(int argc, char **argv) {
    if (argc < 5) {
        fprintf(stderr, "daemon <sock> <cwd> <rows> <cols> <shell> [args...]\n");
        return 2;
    }
    const char *sock = argv[2];
    const char *cwd = argv[3];
    int rows = atoi(argv[4]);
    int cols = argc > 5 ? atoi(argv[5]) : 0;
    int shell_idx = 6;
    if (argc <= shell_idx) {
        fprintf(stderr, "missing shell\n");
        return 2;
    }
    if (rows <= 0) rows = 24;
    if (cols <= 0) cols = 80;

    signal(SIGHUP, SIG_IGN);
    signal(SIGPIPE, SIG_IGN);
    setsid();
    try_escape_cgroup();

    int master;
    char slavename[128];
    if (open_pty(&master, slavename, sizeof(slavename)) < 0) {
        fprintf(stderr, "open_pty failed: %s\n", strerror(errno));
        return 3;
    }

    pid_t child = fork();
    if (child < 0) {
        fprintf(stderr, "fork failed\n");
        return 3;
    }
    if (child == 0) {
        setsid();
        int slave = open(slavename, O_RDWR);
        if (slave < 0) _exit(4);
        ioctl(slave, TIOCSCTTY, 0);
        dup2(slave, 0);
        dup2(slave, 1);
        dup2(slave, 2);
        if (slave > 2) close(slave);
        close(master);
        if (cwd && cwd[0]) chdir(cwd);
        execv(argv[shell_idx], &argv[shell_idx]);
        _exit(127);
    }

    set_win(master, rows, cols);

    int lfd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (lfd < 0) {
        fprintf(stderr, "socket failed\n");
        return 3;
    }
    unlink(sock);
    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    strncpy(addr.sun_path, sock, sizeof(addr.sun_path) - 1);
    if (bind(lfd, (struct sockaddr *) &addr, sizeof(addr)) < 0) {
        fprintf(stderr, "bind failed: %s\n", strerror(errno));
        return 3;
    }
    chmod(sock, 0600);
    {
        char dirbuf[256];
        snprintf(dirbuf, sizeof(dirbuf), "%s", sock);
        char *slash = strrchr(dirbuf, '/');
        if (slash) {
            *slash = '\0';
            struct stat dst;
            if (stat(dirbuf, &dst) == 0) {
                chown(sock, dst.st_uid, dst.st_gid);
            }
        }
    }
    listen(lfd, 4);

    char pidpath[256];
    snprintf(pidpath, sizeof(pidpath), "%s.pid", sock);
    FILE *pf = fopen(pidpath, "w");
    if (pf) {
        fprintf(pf, "%d\n", (int) getpid());
        fclose(pf);
    }
    {
        char dirbuf[256];
        snprintf(dirbuf, sizeof(dirbuf), "%s", sock);
        char *slash = strrchr(dirbuf, '/');
        if (slash) {
            *slash = '\0';
            struct stat dst;
            if (stat(dirbuf, &dst) == 0) {
                chown(pidpath, dst.st_uid, dst.st_gid);
            }
        }
    }

    int cfd = -1;
    unsigned char obuf[MAX_PAYLOAD];
    unsigned char ibuf[IBUF_CAP];
    size_t ilen = 0;

    for (;;) {
        struct pollfd pfd[3];
        pfd[0].fd = master;
        pfd[0].events = POLLIN;
        pfd[0].revents = 0;
        pfd[1].fd = lfd;
        pfd[1].events = POLLIN;
        pfd[1].revents = 0;
        int n = 2;
        if (cfd >= 0) {
            pfd[2].fd = cfd;
            pfd[2].events = POLLIN;
            pfd[2].revents = 0;
            n = 3;
        }
        int r = poll(pfd, n, -1);
        if (r < 0) {
            if (errno == EINTR) continue;
            break;
        }

        if (pfd[0].revents & (POLLIN | POLLHUP | POLLERR)) {
            ssize_t k = read(master, obuf, sizeof(obuf));
            if (k > 0) {
                if (cfd >= 0 && write_all(cfd, obuf, (size_t) k) < 0) {
                    close(cfd);
                    cfd = -1;
                }
            } else if (k == 0 || (k < 0 && errno != EINTR && errno != EAGAIN)) {
                if (cfd >= 0) close(cfd);
                close(master);
                close(lfd);
                unlink(sock);
                unlink(pidpath);
                return 0;
            }
        }

        if (pfd[1].revents & POLLIN) {
            int nc = accept(lfd, NULL, NULL);
            if (nc >= 0) {
                if (cfd >= 0) close(cfd);
                cfd = nc;
                ilen = 0;
            }
        }

        if (n == 3 && (pfd[2].revents & (POLLIN | POLLHUP | POLLERR))) {
            size_t room = sizeof(ibuf) - ilen;
            if (room == 0) {
                ilen = 0;
                room = sizeof(ibuf);
            }
            ssize_t k = read(cfd, ibuf + ilen, room);
            if (k <= 0) {
                close(cfd);
                cfd = -1;
                ilen = 0;
            } else {
                ilen += (size_t) k;
                size_t pos = 0;
                while (pos < ilen) {
                    unsigned char type = ibuf[pos];
                    if (type == FRAME_DATA) {
                        if (ilen - pos < 5) break;
                        unsigned int len =
                            ibuf[pos + 1] | (ibuf[pos + 2] << 8) |
                            (ibuf[pos + 3] << 16) | (ibuf[pos + 4] << 24);
                        if (ilen - pos - 5 < len) break;
                        if (len > 0 && write_all(master, ibuf + pos + 5, len) < 0) {
                        }
                        pos += 5 + len;
                    } else if (type == FRAME_RESIZE) {
                        if (ilen - pos < 5) break;
                        int rr = ibuf[pos + 1] | (ibuf[pos + 2] << 8);
                        int cc = ibuf[pos + 3] | (ibuf[pos + 4] << 8);
                        set_win(master, rr, cc);
                        pos += 5;
                    } else {
                        pos++;
                    }
                }
                if (pos > 0) {
                    memmove(ibuf, ibuf + pos, ilen - pos);
                    ilen -= pos;
                }
            }
        }
    }

    if (cfd >= 0) close(cfd);
    close(master);
    close(lfd);
    unlink(sock);
    unlink(pidpath);
    return 0;
}

static void send_resize(int s, int tty_fd) {
    struct winsize ws;
    if (ioctl(tty_fd, TIOCGWINSZ, &ws) < 0) return;
    unsigned char msg[5];
    msg[0] = FRAME_RESIZE;
    msg[1] = (unsigned char) (ws.ws_row & 0xff);
    msg[2] = (unsigned char) ((ws.ws_row >> 8) & 0xff);
    msg[3] = (unsigned char) (ws.ws_col & 0xff);
    msg[4] = (unsigned char) ((ws.ws_col >> 8) & 0xff);
    write_all(s, msg, 5);
}

static int cmd_attach(int argc, char **argv) {
    if (argc < 3) {
        fprintf(stderr, "attach <sock>\n");
        return 2;
    }
    int s = unix_connect(argv[2]);
    if (s < 0) {
        fprintf(stderr, "connect failed: %s\n", strerror(errno));
        return 2;
    }

    signal(SIGPIPE, SIG_IGN);
    signal(SIGWINCH, on_winch);

    struct termios saved;
    int have_saved = tcgetattr(STDIN_FILENO, &saved) == 0;
    set_raw(STDIN_FILENO);
    send_resize(s, STDIN_FILENO);

    unsigned char buf[MAX_PAYLOAD];
    for (;;) {
        if (g_winch) {
            g_winch = 0;
            send_resize(s, STDIN_FILENO);
        }
        struct pollfd pfd[2];
        pfd[0].fd = STDIN_FILENO;
        pfd[0].events = POLLIN;
        pfd[0].revents = 0;
        pfd[1].fd = s;
        pfd[1].events = POLLIN;
        pfd[1].revents = 0;
        int r = poll(pfd, 2, 500);
        if (r < 0) {
            if (errno == EINTR) continue;
            break;
        }
        if (pfd[0].revents & (POLLIN | POLLHUP | POLLERR)) {
            ssize_t k = read(STDIN_FILENO, buf + 5, sizeof(buf) - 5);
            if (k > 0) {
                buf[0] = FRAME_DATA;
                buf[1] = (unsigned char) (k & 0xff);
                buf[2] = (unsigned char) ((k >> 8) & 0xff);
                buf[3] = (unsigned char) ((k >> 16) & 0xff);
                buf[4] = (unsigned char) ((k >> 24) & 0xff);
                if (write_all(s, buf, (size_t) k + 5) < 0) break;
            } else if (k == 0) {
                break;
            } else if (errno != EINTR && errno != EAGAIN) {
                break;
            }
        }
        if (pfd[1].revents & (POLLIN | POLLHUP | POLLERR)) {
            ssize_t k = read(s, buf, sizeof(buf));
            if (k > 0) {
                if (write_all(STDOUT_FILENO, buf, (size_t) k) < 0) break;
            } else {
                break;
            }
        }
    }

    if (have_saved) tcsetattr(STDIN_FILENO, TCSANOW, &saved);
    close(s);
    return 0;
}

static int cmd_probe(int argc, char **argv) {
    if (argc < 3) return 2;
    int s = unix_connect(argv[2]);
    if (s < 0) return 1;
    close(s);
    return 0;
}

static int cmd_kill(int argc, char **argv) {
    if (argc < 3) return 2;
    const char *sock = argv[2];
    char pidpath[256];
    snprintf(pidpath, sizeof(pidpath), "%s.pid", sock);
    int pid = 0;
    FILE *f = fopen(pidpath, "r");
    if (f) {
        if (fscanf(f, "%d", &pid) != 1) pid = 0;
        fclose(f);
    }
    if (pid > 0) {
        kill(pid, SIGTERM);
        usleep(200000);
        kill(pid, SIGKILL);
    }
    unlink(sock);
    unlink(pidpath);
    return 0;
}

static int cmd_pty(int argc, char **argv) {
    /* pty <cwd> <rows> <cols> <shell> [args...]
     * Bridges a fresh PTY to stdin/stdout so a privileged host process (e.g. a
     * Shizuku user service) can relay it over binder. Optional control socket
     * path is taken from $TERMBRIDGE_CTRL; it accepts "rows cols\n" to resize.
     */
    if (argc < 6) {
        fprintf(stderr, "pty <cwd> <rows> <cols> <shell> [args...]\n");
        return 2;
    }
    const char *cwd = argv[2];
    int rows = atoi(argv[3]);
    int cols = atoi(argv[4]);
    int shell_idx = 5;
    if (rows <= 0) rows = 24;
    if (cols <= 0) cols = 80;

    signal(SIGPIPE, SIG_IGN);
    signal(SIGHUP, SIG_IGN);
    signal(SIGCHLD, SIG_IGN);

    int master;
    char slavename[128];
    if (open_pty(&master, slavename, sizeof(slavename)) < 0) {
        fprintf(stderr, "open_pty failed: %s\n", strerror(errno));
        return 3;
    }

    pid_t child = fork();
    if (child < 0) {
        fprintf(stderr, "fork failed\n");
        return 3;
    }
    if (child == 0) {
        setsid();
        int slave = open(slavename, O_RDWR);
        if (slave < 0) _exit(4);
        ioctl(slave, TIOCSCTTY, 0);
        dup2(slave, 0);
        dup2(slave, 1);
        dup2(slave, 2);
        if (slave > 2) close(slave);
        close(master);
        if (cwd && cwd[0]) chdir(cwd);
        execv(argv[shell_idx], &argv[shell_idx]);
        _exit(127);
    }

    set_win(master, rows, cols);

    int ctrl = -1;
    const char *ctrlpath = getenv("TERMBRIDGE_CTRL");
    if (ctrlpath && ctrlpath[0]) {
        ctrl = socket(AF_UNIX, SOCK_STREAM, 0);
        if (ctrl >= 0) {
            struct sockaddr_un addr;
            memset(&addr, 0, sizeof(addr));
            addr.sun_family = AF_UNIX;
            strncpy(addr.sun_path, ctrlpath, sizeof(addr.sun_path) - 1);
            unlink(ctrlpath);
            if (bind(ctrl, (struct sockaddr *) &addr, sizeof(addr)) < 0) {
                close(ctrl);
                ctrl = -1;
            } else {
                chmod(ctrlpath, 0600);
                listen(ctrl, 2);
            }
        }
    }

    int InEof = 0;
    for (;;) {
        struct pollfd pfd[3];
        int nfd = 0;
        int mi = nfd;
        pfd[nfd].fd = master;
        pfd[nfd].events = POLLIN;
        pfd[nfd].revents = 0;
        nfd++;
        int ii = -1;
        if (!InEof) {
            ii = nfd;
            pfd[nfd].fd = 0;
            pfd[nfd].events = POLLIN;
            pfd[nfd].revents = 0;
            nfd++;
        }
        int ci = -1;
        if (ctrl >= 0) {
            ci = nfd;
            pfd[nfd].fd = ctrl;
            pfd[nfd].events = POLLIN;
            pfd[nfd].revents = 0;
            nfd++;
        }

        int r = poll(pfd, (nfds_t) nfd, -1);
        if (r < 0) {
            if (errno == EINTR) {
                if (g_winch) {
                    g_winch = 0;
                    ioctl(master, TIOCGWINSZ, &g_ws);
                }
                continue;
            }
            break;
        }

        if (pfd[mi].revents & POLLIN) {
            char buf[8192];
            ssize_t n = read(master, buf, sizeof(buf));
            if (n <= 0) break;
            if (write_all(1, buf, (size_t) n) < 0) break;
        }
        if (pfd[mi].revents & (POLLHUP | POLLERR | POLLNVAL)) break;

        if (ii >= 0 && (pfd[ii].revents & (POLLIN))) {
            char buf[8192];
            ssize_t n = read(0, buf, sizeof(buf));
            if (n <= 0) {
                InEof = 1;
            } else if (write_all(master, buf, (size_t) n) < 0) {
                break;
            }
        } else if (ii >= 0 && (pfd[ii].revents & (POLLHUP | POLLERR | POLLNVAL))) {
            InEof = 1;
        }

        if (ci >= 0 && (pfd[ci].revents & POLLIN)) {
            int conn = accept(ctrl, NULL, NULL);
            if (conn >= 0) {
                char cb[64];
                ssize_t n = read(conn, cb, sizeof(cb) - 1);
                if (n > 0) {
                    cb[n] = '\0';
                    int rr = 0, cc = 0;
                    if (sscanf(cb, "%d %d", &rr, &cc) == 2 && rr > 0 && cc > 0) {
                        set_win(master, rr, cc);
                    }
                }
                close(conn);
            }
        }
    }

    kill(child, SIGHUP);
    kill(child, SIGKILL);
    if (ctrlpath && ctrlpath[0]) unlink(ctrlpath);
    if (ctrl >= 0) close(ctrl);
    return 0;
}

int main(int argc, char **argv) {
    if (argc < 2) {
        fprintf(stderr, "usage: termbridge <daemon|attach|probe|kill|pty> ...\n");
        return 2;
    }
    if (strcmp(argv[1], "daemon") == 0) return cmd_daemon(argc, argv);
    if (strcmp(argv[1], "attach") == 0) return cmd_attach(argc, argv);
    if (strcmp(argv[1], "probe") == 0) return cmd_probe(argc, argv);
    if (strcmp(argv[1], "kill") == 0) return cmd_kill(argc, argv);
    if (strcmp(argv[1], "pty") == 0) return cmd_pty(argc, argv);
    fprintf(stderr, "unknown mode: %s\n", argv[1]);
    return 2;
}
