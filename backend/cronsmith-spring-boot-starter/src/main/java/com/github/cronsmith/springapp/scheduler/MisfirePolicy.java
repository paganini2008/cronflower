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
package com.github.cronsmith.springapp.scheduler;

import com.github.cronsmith.utils.EnumConstant;

/**
 * 
 * What to do with a fire time that has already passed by the time the scheduler gets to it.
 * 
 * <p>
 * A misfire is normal rather than exceptional: the process was down, a GC pause overran the tick,
 * or the machine was suspended. Without a policy the choice is made by accident, which is how a
 * scheduler ends up replaying a day of missed runs the moment it comes back up.
 * 
 * @Description: MisfirePolicy
 * @Author: Fred Feng
 * @Date: 24/08/2026
 * @Version 1.0.0
 */
public enum MisfirePolicy implements EnumConstant {

    /**
     * Run once immediately, then carry on with the normal schedule. Missed occurrences beyond the
     * first are dropped. This is the default.
     */
    FIRE_ONCE_NOW,

    /**
     * Skip what was missed and wait for the next scheduled time.
     */
    SKIP,

    /**
     * Run once for every occurrence that was missed. Only safe for tasks that are cheap and
     * genuinely need to catch up.
     */
    FIRE_ALL;

    @Override
    public Object getValue() {
        return name();
    }

    @Override
    public String getRepr() {
        return name();
    }

}
