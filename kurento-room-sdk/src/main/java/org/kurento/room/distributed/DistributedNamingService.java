package org.kurento.room.distributed;

import org.kurento.room.distributed.interfaces.IDistributedNamingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Created by sturiale on 02/12/16.
 */
@Component
public class DistributedNamingService implements IDistributedNamingService {

	@Value("${hazelcast.naming.prefix:@null}")
	private String namespacePrefix;

	@Override
	public String getName(final String suffix) {
		return namespacePrefix != null ? namespacePrefix + "-" + suffix : suffix;
	}
}
