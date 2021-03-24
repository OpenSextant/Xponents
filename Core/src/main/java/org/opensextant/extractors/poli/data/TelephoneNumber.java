/*
 *
 * Copyright 2012-2013 The MITRE Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.opensextant.extractors.poli.data;

import org.opensextant.extractors.poli.PoliMatch;
import org.opensextant.util.TextUtils;

/**
 * @author Marc C. Ubaldino, MITRE, ubaldino at mitre dot org
 */
public class TelephoneNumber extends PoliMatch {

    public TelephoneNumber() {
        super();
        normal_case = PoliMatch.UPPER_CASE;
    }

    public TelephoneNumber(String m) {
        super(m);
        normal_case = PoliMatch.UPPER_CASE;
    }

    public TelephoneNumber(java.util.Map<String, String> elements, String m) {
        this(m);
        this.match_groups = elements;
    }

    @Override
    public void normalize() {
        super.normalize();

        String area = match_groups.get("tel_area");
        String nmbr = match_groups.get("tel_num");
        StringBuilder _buf = new StringBuilder();
        if (area != null) {
            area = TextUtils.removeAny(area, "()");
            _buf.append(area);
            _buf.append("-");
        }
        _buf.append(nmbr);

        this.textnorm = _buf.toString().replace(".", "-");
    }
}
