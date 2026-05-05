#include "client.h"
#include "validate.hpp"
#define CPPHTTPLIB_OPENSSL_SUPPORT
#include "httplib.h"
#define STB_IMAGE_WRITE_IMPLEMENTATION
#include "stb_image_write.h"
extern Packet packet;
extern bool has_packet;
std::atomic<bool> imageMode{false};

int main()
{ 

    int uFD {open(DEVICE, O_RDWR | O_NOCTTY)};
    check("usb", uFD);
    struct termios tty;
    setTermios(&tty, uFD);
    std::thread collector(collectorThread, uFD, std::ref(mutex));



    httplib::SSLServer svr("cert.pem", "key.pem");

    svr.Get("/telemetry", [&](const httplib::Request&, httplib::Response& res) {

    Packet copy;
     {
            std::lock_guard<std::mutex> lock(mutex);

            if (!has_packet) {
                res.set_content("{\"valid\":false}", "application/json");
                return;
            }

            copy = packet;
        }

    int hotX {0};
    int hotY {0};
    u8 hotVal {0};

    for (int r = 0; r < FULL_ROWS; r++)
    {
      for (int c = 0; c < COLUMNS; c++) 
      {
        if (copy.img[r][c] > hotVal) 
        {
            hotVal = copy.img[r][c];
            hotY = r;
            hotX = c;
        } 
      }
    }

    std::string json = "{";
    json += "\"max\":"  + std::to_string(kelvinToF(copy.header.max)) + ",";
    json += "\"avg\":"  + std::to_string(kelvinToF(copy.header.avg)) + ",";
    json += "\"min\":"  + std::to_string(kelvinToF(copy.header.min)) + ",";
    json += "\"hotX\":" + std::to_string(hotX) + ",";
    json += "\"hotY\":" + std::to_string(hotY);
    json += "}";
    res.set_content(json, "application/json");
    });

    svr.Post("/image_mode_on", [&](const httplib::Request&, httplib::Response& res) {
        imageMode.store(true, std::memory_order_release);
        res.set_content("{\"status\":\"image_mode_on\"}", "application/json");
    });

    svr.Post("/image_mode_off", [&](const httplib::Request&, httplib::Response& res) {
        imageMode.store(false, std::memory_order_release);
        res.set_content("{\"status\":\"image_mode_off\"}", "application/json");
    });

    svr.Get("/image", [&](const httplib::Request&, httplib::Response& res) {
    Packet copy;
    bool hasData = false;

    {
        std::lock_guard<std::mutex> lock(mutex);
        if (has_packet) {
            copy = packet;
            hasData = true;
        }
    }

    if (!hasData) {
        res.status = 503;
        res.set_content("{\"error\":\"no_image_yet\"}", "application/json");
        return;
    }


    std::vector<unsigned char> rgb(FULL_ROWS * COLUMNS * 3);
    unsigned char* ptr = rgb.data();

    for (int r = 0; r < FULL_ROWS; r++) {
        for (int c = 0; c < COLUMNS; c++) {
            float norm = copy.img[r][c] / 255.0f;
            if (norm < 0) norm = 0;
            if (norm > 1) norm = 1;

            u8 r8, g8, b8;
            heatColor(norm, &r8, &g8, &b8);

            *ptr++ = r8;
            *ptr++ = g8;
            *ptr++ = b8;
        }
    }

    int png_len = 0;
    unsigned char* png_data = stbi_write_png_to_mem(
        rgb.data(), COLUMNS * 3, COLUMNS, FULL_ROWS, 3, &png_len
    );

    if (png_data && png_len > 0) {
        res.set_content((const char*)png_data, png_len, "image/png");
        free(png_data);
    } else {
        res.status = 500;
        res.set_content("{\"error\":\"png_encode_failed\"}", "application/json");
    }
});

    svr.set_mount_point("/", "./www");

    svr.listen("0.0.0.0", 8080);
        
    return 0; 
}
