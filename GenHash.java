import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class GenHash {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String hash = encoder.encode("admin@123");
        System.out.println("HASH:" + hash);
        System.out.println("VERIFY:" + encoder.matches("admin@123", hash));
        System.out.println("VERIFY_OLD:" + encoder.matches("admin@123", "$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2"));
    }
}
