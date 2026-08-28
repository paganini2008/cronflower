package com.github.cronsmith.springapp.scheduler;

/**
 * How the leader picks which executor instance of an application runs a task. Modelled on
 * openspreader's {@code LoadBalancer}: the stateful ones (round-robin, weighted, consistent-hash)
 * are backed by it directly; {@code FIRST}/{@code LAST} are trivial and handled here.
 *
 * @Description: RoutingStrategy
 * @Author: Fred Feng
 * @Date: 26/08/2026
 * @Version 1.0.0
 */
public enum RoutingStrategy {
  /** Always the first live instance (stable target). */
  FIRST,
  /** Always the last live instance. */
  LAST,
  /** Round-robin — even spread. The default. */
  ROUND_ROBIN,
  /** Random. */
  RANDOM,
  /** Consistent hash on a routing key (the task group): the same task sticks to one executor. */
  CONSISTENT_HASH,
  /** Weighted — stronger executors (higher {@code cronsmith.client.weight}) get proportionally more. */
  WEIGHTED,
}
