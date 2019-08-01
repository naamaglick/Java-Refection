package Solution;

import Provided.*;
import org.junit.ComparisonFailure;

import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;

public class StoryTesterImpl implements StoryTester {

    private boolean compareAnnotationWithLine(String[] an_words, String[] line_words) {
        //assert (an_words != null && line_words != null);
        int an_i = 0, line_i = 1;

        while (an_i < an_words.length && line_i < line_words.length) {
            if (an_words[an_i].startsWith("&")) {
                // case an argument
                ++an_i;
                ++line_i;
                // if next word is "and", continue as usual
                // if next word is "or", reset line_i
                if (line_i < line_words.length && line_words[line_i].equals("or")) {
                    if (an_i != an_words.length) {
                        // case the annotation is longer than the line before the "or"
                        return false;
                    }
                    an_i = 0;
                    line_i++;
                }
                continue;
            }
            if (!an_words[an_i].equals(line_words[line_i])) return false;
            ++an_i;
            ++line_i;
        }
        return an_i == an_words.length && line_i == line_words.length;
    }

    private Class<?> getFirstWord(String line) throws WordNotFoundException {
        //assert (line != null);
        String first_word = line.substring(0, line.indexOf(" "));
        if (Given.class.getSimpleName().equals(first_word))
            return Given.class;
        if (When.class.getSimpleName().equals(first_word))
            return When.class;
        if (Then.class.getSimpleName().equals(first_word))
            return Then.class;

        throw new WordNotFoundException(); // shouldn't get here
    }

    private String getAnnotationValue(Method m, Class<?> AnnoType) {
        if (AnnoType.equals(Given.class)) {
            Given annotation = m.getAnnotation(Given.class);
            if (annotation == null) return null;
            return annotation.value();
        }
        if (AnnoType.equals(When.class)) {
            When annotation = m.getAnnotation(When.class);
            if (annotation == null) return null;
            return annotation.value();
        }
        if (AnnoType.equals(Then.class)) {
            Then annotation = m.getAnnotation(Then.class);
            if (annotation == null) return null;
            return annotation.value();
        }
        return null;
    }

    private Method findMethod(Class<?> testClass, String line)
            throws WordNotFoundException {
        //assert (testClass != null && line != null);
        Class<?> line_class = getFirstWord(line);
        for (Method m : testClass.getDeclaredMethods()) {
            String value = getAnnotationValue(m, line_class);
            if (value == null) continue;
            if (compareAnnotationWithLine(value.split(" "), line.split(" "))) {
                return m;
            }
        }
        return null; // the right method does not exist in testClass
    }

    private Method findUpperMethod(Class<?> testClass, String line)
            throws WordNotFoundException {
        //assert (line != null);
        if (testClass == null)
            switch (getFirstWord(line).getSimpleName()) {
                case "Given":
                    throw new GivenNotFoundException();
                case "When":
                    throw new WhenNotFoundException();
                case "Then":
                    throw new ThenNotFoundException();
                default:
                    return null; // shouldn't get here
            }
        Method m = findMethod(testClass, line);
        // recursive call towards parent:
        return (m == null) ? findUpperMethod(testClass.getSuperclass(), line) : m;
    }

    static private boolean lineContainsOR(String line) {
        return line.contains(" or ");
    }

    static private String[] getArgumentsWithoutOR(String line) {
        //assert (line != null && !lineContainsOR(line));
        String[] words = line.split(" ");
        List<String> res = new ArrayList<>();
        for (int i = 0; i < words.length; i++) {
            if (i + 1 == words.length) {
                res.add(words[i]);
                break;
            }
            if (words[i + 1].equals("and")) {
                res.add(words[i]);
            }
        }
        return res.stream().toArray(String[]::new);
    }

    private String[][] getArgumentsWithOR(String line) {
        //assert (line != null);
        String[] or_parts = line.split(" or ");
        return Arrays
                .stream(or_parts)
                .map(StoryTesterImpl::getArgumentsWithoutOR)
                .toArray(String[][]::new);
    }

    private Object[] fixArgumentsToMethod(Method m, String[] args) {
        //assert (m != null && args != null);
        // Making args array for the method: from string[],
        // to object[] of string/integer

        Object[] ready_args = new Object[args.length];
        int i = 0;

        for (Class<?> param_type : m.getParameterTypes()) {
            if (param_type == String.class) {
                ready_args[i] = args[i];
            } else if (param_type == Integer.class) {
                // converting string to an int
                ready_args[i] = Integer.parseInt(args[i]);
            } else {
                // Method arg is not of type String or Integer!
                return null; // shouldn't get here
            }
            ++i;
        }
        return ready_args;
    }

    private ComparisonFailure invokeMethodAux(Object obj, Method m, String[] args) {
        // returns null for: Given, When, a successful Then
        // returns ComparisonFailure only for a failed Then
        //assert (obj != null && m != null && args != null);
        // Making and args array for the method
        Object[] ready_args = fixArgumentsToMethod(m, args);

        try {
            m.setAccessible(true);
            m.invoke(obj, ready_args);
        } catch (IllegalAccessException e) {
            return null; // shouldn't get here
        } catch (InvocationTargetException e) {
            if (e.getCause().getClass() == org.junit.ComparisonFailure.class) {
                return (org.junit.ComparisonFailure) e.getCause();
            }
            return null; // shouldn't get here
        }
        return null; // success
    }

    private Object createNewSubObject(Class<?> testClass, Object parent) {
        // creating an inner class instance using the parent obj
        //assert (testClass != null && parent != null);
        try {
            Constructor<?> con = testClass.getConstructor(parent.getClass());
            con.setAccessible(true);
            return con.newInstance(parent);
        } catch (IllegalAccessException | InstantiationException
                | InvocationTargetException | NoSuchMethodException e) {
            return null; // shouldn't get here
        }
    }

    private Object createNewObject(Class<?> testClass) {
        if (testClass == null) return null;
        try {
            Constructor<?> con = testClass.getConstructor();
            con.setAccessible(true);
            return con.newInstance(); // Assuming can be created without arguments
        } catch (IllegalAccessException | InstantiationException
                | InvocationTargetException | NoSuchMethodException e) {
            // if got here => cannot create an instance of the specific class
            // solution: go to parent class and create enclosing instance
            Object parent = createNewObject(testClass.getEnclosingClass()); // goes up till can create new instance
            return createNewSubObject(testClass, parent); // goes down till gets back to the right class
        }
    }

    private Object backupObjectByCopy(Object obj) {
        if (obj == null) return null;
        Class<?> c = obj.getClass();
        try {
            Constructor con = c.getDeclaredConstructor(c);
            con.setAccessible(true);
            return con.newInstance(obj);
        } catch (IllegalAccessException | InstantiationException
                | InvocationTargetException | NoSuchMethodException e) {
            return obj; // if there isn't copy constructor => copy reference
        }
    }

    private Object backupObjectByClone(Object obj) {
        // this method tries to activate CLONE
        if (obj == null) return null;
        Class<?> c = obj.getClass();
        try {
            Method m = c.getDeclaredMethod("clone");
            // assuming there is a clone since it has Cloneable interface
            m.setAccessible(true);
            return m.invoke(obj);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            return null; // shouldn't get here
        }
    }

    private Set<Field> getAllFields(Class<?> testClass) {
        //assert (testClass != null);
        Set<Field> fields = new HashSet<>(Arrays.asList(testClass.getDeclaredFields()));
        Class<?> super_class = testClass.getSuperclass();
        while (super_class != null) {
            fields.addAll(Arrays.stream(super_class.getDeclaredFields()).collect(Collectors.toSet()));
            super_class = super_class.getSuperclass();
        }
        return fields;
    }

    private void restoreObject(Object obj, Map<Field, Object> map) {
        if (obj == null || map.isEmpty()) return;

        for (Field f : obj.getClass().getDeclaredFields()) {
            //assert (map.containsKey(f));
            f.setAccessible(true);
            Object data = map.get(f);
            try {
                if (data instanceof  Cloneable)
                    f.set(obj, backupObjectByClone(data));
                else
                    f.set(obj, backupObjectByCopy(data));
            } catch (IllegalAccessException e) {
                // shouldn't get here
            }
        }

    }

    private Map<Field, Object> backupObject(Object obj) {
        Map<Field, Object> map = new HashMap<>();
        for (Field f : obj.getClass().getDeclaredFields()) {
            f.setAccessible(true);
            try {
                Object data = f.get(obj);
                if (data instanceof  Cloneable)
                    map.put(f, backupObjectByClone(data));
                else
                    map.put(f, backupObjectByCopy(data));
            } catch (IllegalAccessException e) {
                return null; // shouldn't get here
            }
        }
        return map;
    }

    private Object backupObject2(Object old_obj) {
        // this method tries to back up , this is the main backup method
        if (old_obj == null) return null;
        Object new_obj = createNewObject(old_obj.getClass());
        //assert (new_obj != null);
        try {
            for (Field f : old_obj.getClass().getDeclaredFields() /*getAllFields(old_obj.getClass())*/) {
                f.setAccessible(true);
                //if (Cloneable.class.isAssignableFrom(f.getType()))
                //if (f.getType().isInstance(Cloneable.class))
                if (f.get(old_obj) instanceof  Cloneable)
                    f.set(new_obj, backupObjectByClone(f.get(old_obj)));
                else
                    f.set(new_obj, backupObjectByCopy(f.get(old_obj)));
            }
        } catch (IllegalAccessException e) {
            return null; // shouldn't get here
        }
        return new_obj;
    }

    private StoryTestExceptionImpl invokeMethod(Object obj, Method m, String line)
            throws WordNotFoundException {
        String[][] all_args = getArgumentsWithOR(line);

        // Case Given or When: (doesnt throw)
        if (getFirstWord(line) != Then.class) {
            //assert (all_args.length == 1);
            invokeMethodAux(obj, m, all_args[0]); // always returns null
            return null;
        }

        // Case Then method: (might throw)
        int method_failed_counter = 0;
        List<String> expected = new LinkedList<>(), actual = new LinkedList<>();
        for (String[] args : all_args) {
            ComparisonFailure e = invokeMethodAux(obj, m, args);
            if (e == null)
                return null; // Then succeeded => don't need to run next "or" parts
            expected.add(e.getExpected());
            actual.add(e.getActual());
            ++method_failed_counter;
        }
        //assert (method_failed_counter == all_args.length);
        return new StoryTestExceptionImpl(line, expected, actual);
    }

    private boolean storyIsLegal(String[] lines) {
        /* assuming story is legal section. */
        //assert (lines.length >= 2);
        //assert (lines[0].startsWith("Given") && !lineContainsOR(lines[0]));
        //assert (lines[1].startsWith("When") && !lineContainsOR(lines[0]));
        //assert (lines[lines.length - 1].startsWith("Then"));
        return true;
    }

    @Override
    public void testOnInheritanceTree(String story, Class<?> testClass) throws Exception {
        if (story == null || testClass == null)
            throw new IllegalArgumentException();
        //assert (storyIsLegal(story.split("\n")));
        Object obj = createNewObject(testClass);
        String[] lines = story.split("\n");
        // Given
        Method m = findUpperMethod(testClass, lines[0]);
        invokeMethod(obj, m, lines[0]); // invokeMethod never fails for Given
        Map<Field, Object> backup_map = backupObject(obj);
        // Loop for When~Then + using the backup
        int failed_Then_counter = 0;
        StoryTestExceptionImpl e = null;

        for (int i = 1; i < lines.length; i++) {
            //assert (!lines[i].startsWith("Given")); // assuming story is legal.
            if (lines[i - 1].startsWith("Then") && lines[i].startsWith("When")) {
                backup_map = backupObject(obj); // backup between then -> when
            }
            m = findUpperMethod(testClass, lines[i]);
            StoryTestExceptionImpl new_e = invokeMethod(obj, m, lines[i]);

            if (new_e == null) continue; // line success
            // Case Then failed (When cannot fail by pdf)
            if (e == null) e = new_e; // save only the first Then that failed
            restoreObject(obj, backup_map); // Then failed => restore obj
            failed_Then_counter++;
        }

        if (failed_Then_counter > 0) {
            e.setNumFail(failed_Then_counter);
            throw e;
        }
    }


    @Override
    public void testOnNestedClasses(String story, Class<?> testClass) throws Exception {
        if (story == null || testClass == null)
            throw new IllegalArgumentException();
        try {
            findUpperMethod(testClass, story.substring(0, story.indexOf("\n")));
            // case Given exist => start working
            testOnInheritanceTree(story, testClass);
        } catch (GivenNotFoundException e) {
            // case Given doesn't exist => keep searching
            Class<?>[] sub_classes = testClass.getDeclaredClasses();
            // DFS search
            for (Class<?> sub_c : sub_classes) {
                // TODO: check if BFS is preferred
                testOnNestedClasses(story, sub_c);
            }
        }
    }
}



















