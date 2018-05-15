package uk.gov.cdp.shadow.user.auth;

import java.util.List;

public interface AuthenticationService {

  void authenticate(String userName, String subject, String bizContext, List<String> groups);
}
