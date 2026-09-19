package com.github.cronflow.springapp.server.pojo;

import java.util.List;

/**
 * One page of DAG runs for the console — total matching count plus the page's rows. Same shape as
 * cronsmith's task list response.
 *
 * @Description: DagRunPage
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public record DagRunPage(int total, List<DagRunView> items) {
}
