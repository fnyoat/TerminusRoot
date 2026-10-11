package com.terminus.root;

import com.terminus.root.IShellCallback;

interface IShellService {
    void start(String shell, in String[] args, in String[] env, String dir, String chroot, IShellCallback callback);
    void write(String data);
    int waitFor();
    void exit();
    void destroy() = 16777114;
}
