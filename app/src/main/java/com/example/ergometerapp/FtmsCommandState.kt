package com.example.ergometerapp.ble

enum class FtmsCommandState {
    IDLE,        // mitään ei odoteta
    BUSY,        // komento lähetetty, vastausta odotetaan
    SUCCESS,     // viimeisin onnistui
    ERROR        // viimeisin epäonnistui
}
