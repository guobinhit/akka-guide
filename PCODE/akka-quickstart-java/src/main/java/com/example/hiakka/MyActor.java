package com.example.hiakka;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;

import java.io.IOException;

/**
 * @author bin.guo
 * @Copyright 易宝支付(YeePay)
 * @date 1/17/19,4:11 PM
 * @description
 */
public class MyActor extends AbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    public static Props props() {
        return Props.create(MyActor.class, MyActor::new);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(String.class, s -> {
                    log.info("This is a String type message, content is {}", s);
                    ActorRef child = getContext().actorOf(MyActor.props(), "child");
                    child.tell(22222, ActorRef.noSender());
                    System.out.println(child);
                })
                .matchAny(o -> {
                    log.info("matchAny, content is {}", o);
                })
                .build();
    }

    public static void main(String[] args) {
        ActorSystem system = ActorSystem.create("mySystem");
//        ActorRef myActor = system.actorOf(MyActor.props(), "myActor");
        ActorRef myActor = system.actorOf(MyActor.props());
        myActor.tell("hello", ActorRef.noSender());

//        ActorRef myActor2 = system.actorOf(MyActor.props(), "myActor2");
        ActorRef myActor2 = system.actorOf(MyActor.props());
        myActor2.tell(1, ActorRef.noSender());

        try {
            System.out.println(">>> Press ENTER to exit <<<");
            System.in.read();
        } catch (IOException ioe) {
            System.out.println("Wow, happen a IO exception!");
        } finally {
            system.terminate();
        }
    }
}
