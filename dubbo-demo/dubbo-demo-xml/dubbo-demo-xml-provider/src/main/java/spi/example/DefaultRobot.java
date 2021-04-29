package spi.example;

public class DefaultRobot implements Robot {
    
    @Override
    public void sayHello() {

        System.out.println("Hello, I am default robot.");
    }
}

