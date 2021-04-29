package spi.example;

import org.apache.dubbo.common.extension.SPI;

@SPI("default")
public interface Robot {
    void sayHello();
}