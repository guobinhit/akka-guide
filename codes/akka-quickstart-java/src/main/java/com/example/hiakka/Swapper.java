package com.example.hiakka;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;

/**
 * @author Charies Gavin
 *         https:github.com/guobinhit
 * @date 6/24/19,2:30 PM
 * @description test unbecome method
 */
public class Swapper extends AbstractLoggingActor {
    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .matchEquals(
                        Swap.class,
                        s -> {
                            log().info("Hi");
                            getContext()
                                    .become(
                                            receiveBuilder()
                                                    .matchEquals(
                                                            Swap.class,
                                                            x -> {
                                                                log().info("Ho");
                                                                getContext()
                                                                        .unbecome(); // resets the latest 'become' (just for fun)
                                                            })
                                                    .build(),
                                            false); // push on top instead of replace
                        })
                .build();
    }

    private class Swap {
    }

    public static void main(String[] args) {
        ActorSystem system = ActorSystem.create("SwapperSystem");
        ActorRef swapper = system.actorOf(Props.create(Swapper.class), "swapper");
        swapper.tell(Swap.class, ActorRef.noSender()); // logs Hi
        swapper.tell(Swap.class, ActorRef.noSender()); // logs Ho
        swapper.tell(Swap.class, ActorRef.noSender()); // logs Hi
        swapper.tell(Swap.class, ActorRef.noSender()); // logs Ho
        swapper.tell(Swap.class, ActorRef.noSender()); // logs Hi
        swapper.tell(Swap.class, ActorRef.noSender()); // logs Ho
        system.terminate();
    }
}
