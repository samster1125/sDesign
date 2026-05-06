# Thermal Monitoring System
**Industry-Sponsored Senior Capstone — Marquette University**  
**Sponsor: Dynamic Ratings**

A real-time thermal monitoring system for medium-voltage electrical 
switchgear inspection. The system captures thermal imaging data from 
a FLIR Lepton 3.1r camera, transmits it over RS-485 to a web 
dashboard, and streams wirelessly to an Android app over Bluetooth

## System Architecture
```mermaid
graph TD
    A[" FLIR Lepton 3.1r\nThermal Camera"]
    B[" Raspberry Pi 4\nFirmware · Packet Builder"]
    C[" Laptop — C++ Web Server\nhttplib · Packet Parser · REST API"]
    D[" Android App\nTemps · Image · Alerts"]
    E[" Browser Dashboard\nLive Temps · Chart · Thermal Image"]

    A -->|12 MHz SPI| B
    B -->|RS-485| C
    B -->|Bluetooth SPP| D
    C -->|HTTPS| E
```
