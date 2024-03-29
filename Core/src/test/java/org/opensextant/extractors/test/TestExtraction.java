package org.opensextant.extractors.test;
/*
 * Copyright 2013 ubaldino.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.opensextant.extraction.TextEntity;

/**
 *
 * @author ubaldino
 */
public class TestExtraction {

    /** */
    public void test() {
        TextEntity o1 = new TextEntity(10, 15);
        TextEntity o2 = new TextEntity(11, 17);

        System.out.println(o2.isWithin(o1));
        System.out.println(o2.isSameMatch(o1));
        System.out.println(o2.isOverlap(o1));
    }

    public static void main(String[] args) {
        new TestExtraction().test();
    }
}
