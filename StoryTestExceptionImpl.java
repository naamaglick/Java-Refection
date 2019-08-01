package Solution;
import Provided.*;
import java.util.List;

public class StoryTestExceptionImpl extends StoryTestException {

    private String line;
    private List<String> expected, actual;
    private int failures;

    StoryTestExceptionImpl(String line, List<String> expected, List<String> actual) {
        this.line = line;
        this.expected = expected;
        this.actual = actual;
        this.failures = 0;
    }

    void setNumFail(int num) {
        assert(num > 0);
        this.failures = num;
    }

    public String getSentance() {
        return line;
    }
    public List<String> getStoryExpected() {
        return expected;
    }
    public List<String> getTestResult() {
        return actual;
    }

    public int getNumFail() {
        return failures;
    }
}
