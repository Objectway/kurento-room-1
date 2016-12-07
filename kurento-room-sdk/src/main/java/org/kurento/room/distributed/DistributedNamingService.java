package org.kurento.room.distributed;

import org.kurento.room.distributed.interfaces.IDistributedNamingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Created by sturiale on 02/12/16.
 */
@Component
public class DistributedNamingService implements IDistributedNamingService {

    @Value("${config.profile}")
    private String profile;

    @Value("${hazelcast.naming.prefix}")
    private String namespacePrefix;

    @Override
    public String getName(final String suffix) {
        return profile + "-" + namespacePrefix + "-" + suffix;
    }
}
