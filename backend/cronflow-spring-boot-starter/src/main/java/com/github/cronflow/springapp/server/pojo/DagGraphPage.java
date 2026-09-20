package com.github.cronflow.springapp.server.pojo;

import java.util.List;

/**
 * One page of registered DAG definitions for the console: the total matching count plus this page's
 * rows. Same shape as {@link DagRunPage} and cronsmith's task list response, so the Workflows list
 * paginates server-side like the Tasks and Runs lists.
 *
 * @Description: DagGraphPage
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public record DagGraphPage(int total, List<DagGraphView> items) {
}
