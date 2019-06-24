package com.example.hiakka;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;

import java.io.IOException;

/**
 * @author Charies Gavin
 *         https:github.com/guobinhit
 * @date 6/24/19,2:14 PM
 * @description test become method
 */
public class HotSwapActor extends AbstractActor {

    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    private static Props props() {
        return Props.create(HotSwapActor.class, HotSwapActor::new);
    }

    private AbstractActor.Receive angry;
    private AbstractActor.Receive happy;

    public HotSwapActor() {
        angry =
                receiveBuilder()
                        .matchEquals(
                                "foo",
                                s -> {
                                    getSender().tell("I am already angry?", getSelf());
                                    log.info("angry foo tell message is: I am already angry?");
                                })
                        .matchEquals(
                                "bar",
                                s -> {
                                    getContext().become(happy);
                                    log.info("angry bar become message to happy.");
                                })
                        .build();

        happy =
                receiveBuilder()
                        .matchEquals(
                                "bar",
                                s -> {
                                    getSender().tell("I am already happy :-)", getSelf());
                                    log.info("happy bar tell message is: I am already happy :-)");
                                })
                        .matchEquals(
                                "foo",
                                s -> {
                                    getContext().become(angry);
                                    log.info("happy foo become message to angry.");
                                })
                        .build();
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .matchEquals("foo", s -> {
                    log.info("match message { " + s + " }");
                    getContext().become(angry);
                })
                .matchEquals("bar", s -> {
                    log.info("match message { " + s + " }");
                    getContext().become(happy);
                })
                .build();
    }

    public static void main(String[] args) {
        ActorSystem system = ActorSystem.create("test-become-system");

        ActorRef ref = system.actorOf(HotSwapActor.props(), "becomeActor");

        ref.tell("foo", ActorRef.noSender());

        try {
            System.out.println(">>> Press ENTER to exit <<<");
            System.in.read();
        } catch (IOException ioe) {
            System.out.println("It's a pity, we come across a IO exception!");
        } finally {
            system.terminate();
        }
    }
}
