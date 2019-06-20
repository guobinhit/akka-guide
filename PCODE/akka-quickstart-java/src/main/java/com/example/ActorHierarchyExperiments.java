package com.example;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;

import java.io.IOException;
import java.util.Optional;

/**
 * @author Charies Gavin
 *         https:github.com/guobinhit
 * @date 1/15/19,6:34 PM
 * @description Actor hierarchy experiments
 */
public class ActorHierarchyExperiments {
    public static void main(String[] args) {
        ActorSystem system = ActorSystem.create("testSystem");

        // test actor references
        ActorRef firstRef = system.actorOf(PrintMyActorRefActor.props(), "first-actor");
        System.out.println("First: " + firstRef);
        firstRef.tell("printit", ActorRef.noSender());

        // test actor lifecycle hooks
        ActorRef first = system.actorOf(StartStopActor1.props(), "first");
        first.tell("stop", ActorRef.noSender());

        // test failure condition
        ActorRef supervisingActor = system.actorOf(SupervisingActor.props(), "supervising-actor");
        supervisingActor.tell("failChild", ActorRef.noSender());

        System.out.println(">>> Press ENTER to exit <<<");

        try {
            System.in.read();
        } catch (IOException ioe) {
            System.out.println("Wow, happen a IO exception!");
        } finally {
            system.terminate();
        }
    }
}

class PrintMyActorRefActor extends AbstractActor {

    static Props props() {
        return Props.create(PrintMyActorRefActor.class, () -> new PrintMyActorRefActor());
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder().
                matchEquals("printit", p -> {
                    ActorRef secondRef = getContext().actorOf(Props.empty(), "second-actor");
                    System.out.println("Second: " + secondRef);
                })
                .build();
    }
}

class StartStopActor1 extends AbstractActor {
    static Props props() {
        return Props.create(StartStopActor1.class, StartStopActor1::new);
    }

    @Override
    public void preStart() {
        System.out.println("first started");
        getContext().actorOf(StartStopActor2.props(), "second");
    }

    @Override
    public void postStop() {
        System.out.println("first stopped");
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder().
                matchEquals("stop", s -> {
                    getContext().stop(getSelf());
                }).build();
    }
}

class StartStopActor2 extends AbstractActor {
    static Props props() {
        return Props.create(StartStopActor2.class, StartStopActor2::new);
    }

    @Override
    public void preStart() {
        System.out.println("second started");
    }

    @Override
    public void postStop() {
        System.out.println("second stopped");
    }

    /**
     * Actor.emptyBehavior is a useful placeholder when we don't
     * want to handle any messages in the actor.
     *
     * @return
     */
    @Override
    public Receive createReceive() {
        return receiveBuilder().build();
    }
}

class SupervisingActor extends AbstractActor {
    static Props props() {
        return Props.create(SupervisingActor.class, SupervisingActor::new);
    }

    ActorRef child = getContext().actorOf(SupervisedActor.props(), "supervised-actor");

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .matchEquals("failChild", f -> {
                    child.tell("fail", getSelf());
                })
                .build();
    }
}

class SupervisedActor extends AbstractActor {
    static Props props() {
        return Props.create(SupervisedActor.class, SupervisedActor::new);
    }

    @Override
    public void preStart() {
        System.out.println("supervised actor started");
    }

    @Override
    public void postStop() {
        System.out.println("supervised actor stopped");
    }

//    @Override
//    public void preRestart(Throwable reason, Optional<Object> message) {
//        System.out.println("Come into preRestart, fail reason is " + reason + " message is " + message);
//    }
//
//    @Override
//    public void postRestart(Throwable reason) {
//        System.out.println("Come into postRestart, fail reason is " + reason);
//    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .matchEquals("fail", f -> {
                    System.out.println("supervised actor fails now");
                    throw new Exception("I failed!");
                })
                .build();
    }
}
