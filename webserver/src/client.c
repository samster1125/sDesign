#include "client.h"

void setTermios(struct termios* tty, int fd)
{
    tcgetattr(fd, tty);

    cfmakeraw(tty);

    cfsetispeed(tty, B115200);
    cfsetospeed(tty, B115200);

    tty->c_cc[VMIN]  = 1;  
    tty->c_cc[VTIME] = 0;

    tcsetattr(fd, TCSANOW, tty);
}

int readAll(int fd, void* buff, size_t bufflen)
{
    int total = 0;
    int remaining = bufflen;
    
    while (remaining > 0)
    {
        int n = read(fd, (u8*)buff+total, remaining);

        if (n < 0)
        {
            perror("Somethng messed up w/ read");
            return -1;
        }

        if (n == 0)
        {
            return 0;
        }

        total += n;
        remaining -= n;
    }
    return 1;
}

void check(const char* s, int fd)
{
       if (fd < 0)
    {
        printf("%s\n", s);
        perror("failed");
    }
}

int writeAll(int fd, void* buf, size_t buflen)
{
    int total = 0;
    size_t remaining = buflen;

    while (remaining > 0)
    {
        int n = write(fd, (u8*)buf+total, remaining);

        if (n < 0)
        {
            return -1;
            perror("something happened w/ writing in writeAll()\n");
        }

        if (n == 0)
        {
          return 0;
        }

        total += n;
        remaining -= n;
    }
    return 1;
}


