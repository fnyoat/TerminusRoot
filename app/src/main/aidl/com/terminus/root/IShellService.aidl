package com.terminus.root;

import com.terminus.root.IShellCallback;

interface IShellService {
    void start(String shell, in String[] args, in String[] env, String dir, String chroot, IShellCallback callback) = 1;
    void write(String data) = 2;
    int waitFor() = 3;
    void exit() = 4;
    void destroy() = 16777114;
}
