package com.example.iot;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

import java.io.IOException;

/**
 * @author Charies Gavin
 *         https:github.com/guobinhit
 * @date 1/16/19,1:41 PM
 * @description main class of iot system
 */
public class IotMain {
    public static void main(String[] args) throws IOException {
        ActorSystem system = ActorSystem.create("iot-system");

        try {
            // Create top level supervisor
            ActorRef supervisor = system.actorOf(IotSupervisor.props(), "iot-supervisor");

            System.out.println("Press ENTER to exit the system");
            System.in.read();
        } finally {
            system.terminate();
        }
    }
}
