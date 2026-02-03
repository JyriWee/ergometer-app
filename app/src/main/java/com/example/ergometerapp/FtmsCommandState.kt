package com.example.ergometerapp.ble

enum class FtmsCommandState {
    IDLE,        // no pending command
    BUSY,        // command sent, awaiting response
    SUCCESS,     // last command succeeded
    ERROR        // last command failed
}
