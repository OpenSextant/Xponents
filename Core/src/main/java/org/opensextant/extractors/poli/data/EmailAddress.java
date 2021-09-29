package org.opensextant.extractors.poli.data;

import org.opensextant.extractors.poli.PoliMatch;

public class EmailAddress extends PoliMatch {

    public EmailAddress() {
        super();
        normal_case = PoliMatch.LOWER_CASE;
    }

    public EmailAddress(String m) {
        super(m);
        normal_case = PoliMatch.LOWER_CASE;
    }

    public EmailAddress(java.util.Map<String, String> elements, String m) {
        this(m);
        this.match_groups = elements;
    }

    @Override
    public void normalize() {
        super.normalize();

        String name = this.match_groups.get("email_local_name");
        String domain = this.match_groups.get("email_domain");

        // Bad definition of pattern:
        if (name == null || domain == null) {
            setFilteredOut(true);
            return;
        }
        // Avoid loose definition of pattern should "a@b" match, it is not allowed.
        // Length checks are important.
        // MAX length is assumed covered by pattern definition.
        if (name.length() < 2 || domain.length() < 2) {
            setFilteredOut(true);
            return;
        }
        if (domain.contains("..") || name.contains("..")) {
            // Allowed only if respective part is quoted, e.g., "name..name"
            setFilteredOut(true);
        }
    }
}
