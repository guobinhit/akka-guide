package com.example.hiakka;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.routing.*;

import scala.concurrent.duration.Duration;

import java.util.concurrent.TimeUnit;


/**
 * @author bin.guo
 * @Copyright 易宝支付(YeePay)
 * @date 8/12/19,2:42 PM
 * @description
 */
public class RouterActor extends AbstractActor {

    /**
     * akka.actor.deployment {
     * /parent/router29 {
     * router = round-robin-pool
     * resizer {
     * lower-bound = 2
     * upper-bound = 15
     * messages-per-resize = 100
     * }
     * }
     * }
     */

    ActorRef router29 =
            getContext()
                    .actorOf(FromConfig.getInstance().props(Props.create(Worker.class)), "router29");

    DefaultResizer resizer = new DefaultResizer(2, 15);
    ActorRef router30 =
            getContext()
                    .actorOf(
                            new RoundRobinPool(5).withResizer(resizer).props(Props.create(Worker.class)),
                            "router30");


    /**
     * akka.actor.deployment {
     * /parent/router31 {
     * router = round-robin-pool
     * optimal-size-exploring-resizer {
     * enabled = on
     * action-interval = 5s
     * downsize-after-underutilized-for = 72h
     * }
     * }
     * }
     */

    ActorRef router31 =
            getContext()
                    .actorOf(FromConfig.getInstance().props(Props.create(Worker.class)), "router31");

    OptimalSizeExploringResizer optimalSizeExploringResizer =
            new DefaultOptimalSizeExploringResizer(2,
                    15,
                    10,
                    Duration.create(5L, TimeUnit.MINUTES),
                    2,
                    1,
                    2,
                    Duration.create(7L, TimeUnit.HOURS),
                    3,
                    1);

    ActorRef router32 =
            getContext().actorOf(
                    new RoundRobinPool(5).withResizer(optimalSizeExploringResizer).props(Props.create(Worker.class)), "router32");

    @Override
    public Receive createReceive() {
        return null;
    }


    private class Worker {
    }
}
