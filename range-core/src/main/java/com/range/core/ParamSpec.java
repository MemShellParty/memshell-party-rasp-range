package com.range.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ParamSpec {
    private final String name;
    private final String label;
    private final String defaultValue;
    private final List<String> options;

    public ParamSpec(String name, String label, String defaultValue, List<String> options) {
        this.name = name;
        this.label = label;
        this.defaultValue = defaultValue;
        this.options = options == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(options));
    }

    public String getName() {
        return name;
    }

    public String getLabel() {
        return label;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public List<String> getOptions() {
        return options;
    }
}
