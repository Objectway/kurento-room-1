package org.kurento.room;

/**
 * Credentials for TURN consumed by CCAS clients.
 */
public class TurnClientCredentials {
    private String stunUrl;
    private String turnUrl;
    private String username;
    private String password;

    public String getStunUrl() {
        return stunUrl;
    }

    public void setStunUrl(String stunUrl) {
        this.stunUrl = stunUrl;
    }

    public String getTurnUrl() {
        return turnUrl;
    }

    public void setTurnUrl(String turnUrl) {
        this.turnUrl = turnUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String toString() {
        return "TurnClientCredentials{" +
                "stunUrl='" + stunUrl + '\'' +
                ", turnUrl='" + turnUrl + '\'' +
                ", username='" + username + '\'' +
                ", password='" + password + '\'' +
                '}';
    }
}
