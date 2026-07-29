import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordReset {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        System.out.println(encoder.encode("prashant.0927"));
    }
}