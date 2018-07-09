package org.kurento.room.api.pojo;

import java.io.Serializable;
import java.util.Objects;

/**
 * Created by Sebastiano Motta on 04/06/2018.
 */
public class KurentoUserId implements Serializable, Comparable<KurentoUserId>{

	private static final long serialVersionUID = 1L;

	private String tenant;
	private String username;

	public KurentoUserId(final String username) {
		this.username = username;
	}

	public KurentoUserId(final String username, final String tenant) {
		this.username = username;
		this.tenant = tenant;
	}

	public String getTenant() {
		return tenant;
	}

	public String getUsername() {
		return username;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		KurentoUserId userId = (KurentoUserId) o;
		return Objects.equals(tenant, userId.tenant) &&
				Objects.equals(username, userId.username);
	}

	@Override
	public int hashCode() {
		return Objects.hash(tenant, username);
	}

	@Override
	public String toString() {
		return "[" + username + " on tenant " + tenant + "]";
	}

	@Override
	public int compareTo(KurentoUserId o) {

		// Compare by tenants (they can be null)
		int tenantComparison = 0;

		// https://stackoverflow.com/questions/7168497/undocumented-string-comparetonull-npe
		if (tenant != null && o.getTenant() != null) {
			tenantComparison = tenant.compareTo(o.getTenant());
		}

		if (tenant == null && o.getTenant() != null) {
			tenantComparison = 1;
		}

		if (tenant != null && o.getTenant() == null) {
			tenantComparison = -1;
		}

		if (tenantComparison != 0) {
			return tenantComparison;
		}

		// Compare by username (they can't be null)
		return username.compareTo(o.getUsername());

	}
}
