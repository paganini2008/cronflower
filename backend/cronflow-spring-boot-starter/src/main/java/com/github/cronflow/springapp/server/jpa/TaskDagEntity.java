/*
 * Copyright 2026 Fred Feng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.cronflow.springapp.server.jpa;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A stored DAG definition. Its own table {@code cf_task_dag}, entirely separate from cronsmith's
 * {@code cs_*} tables — DAG and task are independent features. Picked up by an additive
 * {@code @EntityScan} in the cronflow auto-configuration, so a cronsmith-only server never creates it.
 *
 * @Description: TaskDagEntity
 * @Author: Fred Feng
 * @Version 1.0.0
 */
@Getter
@Setter
@Entity
@Table(name = "cf_task_dag")
public class TaskDagEntity {

    /** {@code application + "/" + graph}. */
    @Id
    @Column(name = "id", length = 512)
    private String id;

    @Column(name = "application", length = 255)
    private String application;

    @Column(name = "graph_name", length = 255)
    private String graph;

    /** The woven definition, serialized (JSON by default). Large text, portable: Hibernate maps this
     *  length to TEXT/MEDIUMTEXT on MySQL, text on PostgreSQL, CLOB/VARCHAR on H2/SQLite. */
    @Column(name = "definition", length = 1_000_000)
    private String definition;

    @Column(name = "format", length = 16)
    private String format = "json";

    @Column(name = "enabled")
    private boolean enabled = true;

    @Column(name = "last_modified")
    private LocalDateTime lastModified;

}
