CC = gcc
CXX = g++

CFLAGS = -Wall -Wextra -std=c11 -g -Iinc -D_DEFAULT_SOURCE
CXXFLAGS = -Wall -Wextra -std=c++17 -g -Iinc

SRC_C = $(wildcard src/*.c)
SRC_CPP = $(wildcard src/*.cpp)

OBJ_C = $(SRC_C:.c=.o)
OBJ_CPP = $(SRC_CPP:.cpp=.o)

OBJ = $(OBJ_C) $(OBJ_CPP)

TARGET = main

all: $(TARGET)

$(TARGET): $(OBJ)
	$(CXX) -o $@ $(OBJ)   # use g++ for linking

%.o: %.c
	$(CC) $(CFLAGS) -c $< -o $@

%.o: %.cpp
	$(CXX) $(CXXFLAGS) -c $< -o $@

clean:
	rm -f $(OBJ) $(TARGET)
