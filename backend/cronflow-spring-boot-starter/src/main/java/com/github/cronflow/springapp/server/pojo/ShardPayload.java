package com.github.cronflow.springapp.server.pojo;

import java.io.Serializable;
import java.util.List;

/**
 * One unit of sharded work sent across the scheduler cluster by the MapReduce job: which executor
 * bean+method to call ({@code graph}/{@code bean}/{@code method}), which output channel the partial
 * lands under, the items to process, and the target shard size. It is serialised as a cluster message,
 * so it carries only data — the dispatch itself is done on the receiving node by the job bean there.
 *
 * @Description: ShardPayload
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public record ShardPayload(String graph, String bean, String method, String output,
        List<Object> items, int size) implements Serializable {
}
