package Main;

import Forwarder.PortForwarder;

public class Main {
    public static void main(String[] args) {

        if (3 > args.length) {
            System.out.println("You didn't enter src port or dst ip/port");
            return;
        }

        PortForwarder forwarder = new PortForwarder(Integer.parseInt(args[0]), args[1], Integer.parseInt(args[2]));

        forwarder.forward();
    }
}
