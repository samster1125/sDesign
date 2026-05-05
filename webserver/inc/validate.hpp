#ifndef VALIDATE_H
#define VALIDATE_H

#include <vector>
#include <memory>
#include <mutex>
#include <atomic>
#include <iostream>
#include "client.h"
extern std::mutex mutex;
extern Packet packet;
extern bool has_packet;
extern std::atomic<bool> imageMode;

template <typename T>
void readB(const void* src, T* dest, size_t * off);
void savePPM(const char *filename, u8 img[FULL_ROWS][COLUMNS]);
float kelvinToF(u16 raw);
void readImg(const u8* buf, u8 img[FULL_ROWS][COLUMNS], size_t* off);
void readBytes(const void* src, void* dest, size_t n, size_t* off);
int isValid(std::vector<u8>& buf, int fd);
void pabort(const char *s);
std::unique_ptr<Packet> parsePacket(const std::vector<u8>& buf);
void collectorThread(int fd, std::mutex& mutex);
void heatColor(float norm, u8 *r8, u8 *g8, u8 *b8);

#endif
