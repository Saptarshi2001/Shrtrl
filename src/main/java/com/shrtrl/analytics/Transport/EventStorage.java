
package com.shrtrl.analytics.Transport;

import com.shrtrl.models.Event;

public interface EventStorage {
    void insertEvent(Event event);
}
