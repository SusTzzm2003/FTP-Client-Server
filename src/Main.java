import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class Main {
    public static void main(String[] args) {
        boolean a;

    }

    private static int getFileCount(List<File> list) {
        int count = 0;
        for (File file : list) {
            if (file.isFile()) {
                count ++;
            } else {
                count += getFileCount(Arrays.asList(Objects.requireNonNull(file.listFiles())));
            }
        }
        return count;
    }
}
