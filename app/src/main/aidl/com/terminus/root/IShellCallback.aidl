package com.terminus.root;

oneway interface IShellCallback {
    void onOutput(String data);
    void onExit(int exitCode);
}
