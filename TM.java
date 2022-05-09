import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

import java.util.*;
import java.io.*;

public class TM {

    public static Instant now = Instant.now();
    public enum CLIENT_COMMAND { INVALID, START, STOP, DESCRIBE, SUMMARY }

    public static void main( String[] args )
    {   
        String fileName = "task_log.txt";

        InputHandler ih = new InputHandler(args);
        CLIENT_COMMAND currentCommand = ih.getCommand();

        if (currentCommand == CLIENT_COMMAND.INVALID) { ih.printUsage(); return; }

        LogFileHandler lfh = new LogFileHandler(fileName);
        List<String> lines = lfh.getLines();

        TaskHandler taskHandler = new TaskHandler(lines, currentCommand, ih.getCommandArgs());
        taskHandler.executeCommand();
        List<String> tasksToSave = new ArrayList<>(taskHandler.getTasksToSave());
        
        lfh.writeLines(tasksToSave);
    }

    public static class LogFileHandler {

        private File taskLogFile;
        private List<String> lines = new ArrayList<>();

        public LogFileHandler(String fn) {
            this.taskLogFile = new File(fn);
            this.readLines();
        }

        private void readLines() {
            try {
                if (this.taskLogFile.createNewFile()) { return; }

                BufferedReader br = new BufferedReader(new FileReader(this.taskLogFile));
                for(String taskLine; (taskLine = br.readLine()) != null; ) { this.lines.add(taskLine); }
                br.close();
            } 
            
            catch (IOException e) { System.out.println(e.getMessage()); }
        }

        public void writeLines(List<String> linesToWrite) {

            try {
                FileOutputStream fos = new FileOutputStream(this.taskLogFile);
                BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos));
                for (String line : linesToWrite) { bw.write(line); bw.newLine(); }
                bw.close();
            }

            catch (IOException e) { System.out.println(e.getMessage()); }
        }

        public ArrayList<String> getLines() {
            return new ArrayList<>(this.lines);
        }
    }

    public static class TaskHandler {
        private List<String> lines;
        private List<String> commandArguments;
        private CLIENT_COMMAND currentCommand;
        private List<Task> tasks = new ArrayList<>();

        public TaskHandler(List<String> lines, CLIENT_COMMAND cc, ArrayList<String> ca) {
            this.lines = lines;
            this.currentCommand = cc;
            this.commandArguments = ca;
            this.convertLinesToTasks();
        }

        public void convertLinesToTasks() {
            for (String line : this.lines) {
                List<String> taskDetails = Arrays.asList(line.split(","));
                if (taskDetails.size() < 2) { continue; }

                Task newTask = new Task(taskDetails);
                this.tasks.add(newTask);
            }
        }

        public Task findTask(String name) {

            for (Task task : this.tasks) {
                if (name.equals(task.getName())) {
                    return task;
                }
            }

            return null;
        }

        public void executeCommand() {
            switch (this.currentCommand) {
                case START:
                    this.startTask(this.commandArguments.get(0));
                    break;
                case DESCRIBE:
                    this.describeTask(this.commandArguments.get(0), this.commandArguments.get(1));
                    break;
                case INVALID:
                    break;
                case STOP:
                    this.stopTask(this.commandArguments.get(0));
                    break;
                case SUMMARY:
                    if(this.commandArguments.size() == 1) { this.summary(this.commandArguments.get(0)); }
                    else { this.summary(); }
                    break;
                default:
                    break;
                
            }
        }

        public void startTask(String name) {
            Task taskToSearchFor = this.findTask(name);
            if (taskToSearchFor == null) {
                Task newTask = new Task(name);
                this.tasks.add(0, newTask);
            } 
            
            else { 
                if (!taskToSearchFor.isActive()) {
                    Task newTask = new Task(name, taskToSearchFor.getRawDuration(), taskToSearchFor.getDescription());
                    this.tasks.remove(taskToSearchFor); 
                    this.tasks.add(0, newTask);
                } 
            }   
        }

        public void stopTask(String name) {
            Task task = this.findTask(name);
            if (task == null) { System.out.println(name + "\t: Does Not Exist"); return; }
            if (!task.isActive()) { System.out.println(name + "\t: Is Not Active"); return; }
            
            task.setEndTime();
            task.taskEndedAddDuration();
        }

        public void describeTask(String name, String description) {
            Task task = this.findTask(name);
            if (task == null) { 
                this.startTask(name); 
                task = this.findTask(name); 
            }

            task.setDescription(description);
        }

        public void summary(String name) {
            Task task = this.findTask(name);
            if (task == null) { return; }

            System.out.printf("""
            Summary for task\t: %s
            Description\t\t: %s
            Total time on task\t: %s
            \n""", task.getName(), task.getDescription(), task.getDurationString());
        }

        public void summary() {
            for (Task task : this.tasks) {
                System.out.printf("""
                Summary for task\t: %s
                Description\t\t: %s
                Total time on task\t: %s
                \n""", task.getName(), task.getDescription(), task.getDurationString());
            }
        }

        public List<String> getTasksToSave() {
            List<String> tasksToSave = new ArrayList<>();
            for (Task task : this.tasks) { 
                tasksToSave.add(task.getTaskDetails()); 
            }

            return tasksToSave;
        }
     }

    public static class Task { 
        private String name;
        private String description;
        private Instant startTime;
        private Instant endTime;
        private Duration totalDuration;

        public Task(List<String> td) {
            this.name = td.get(0);
            this.startTime = Instant.parse(td.get(1));

            try { this.totalDuration = Duration.parse(td.get(2)); }
            catch(IndexOutOfBoundsException | DateTimeParseException e) { this.totalDuration = Duration.ZERO; }

            try { this.endTime = Instant.parse(td.get(3)); } 
            catch(IndexOutOfBoundsException | DateTimeParseException e) { this.endTime = null; }

            try { this.description = td.get(4); }
            catch (IndexOutOfBoundsException e) { this.description = ""; }
        }

        public Task(String name) {
            this.name = name;
            this.startTime = now;
            this.totalDuration = Duration.ZERO;
            this.endTime = null;
            this.description = "";
        }

        public Task(String name, Duration duration, String description) {
            this.name = name;
            this.startTime = now;
            this.totalDuration = duration;
            this.endTime = null;
            this.description = description;
        }

        public String getName() { return this.name; }
        public String getDescription() { return this.description;  }
        public Duration getRawDuration() { return this.totalDuration; }

        public void taskEndedAddDuration() {
            this.totalDuration = this.totalDuration.plus(Duration.between(this.startTime, now));
        }

        public String getDurationString() {

            Duration duration = Duration.ZERO;

            if (this.isActive()) {
                duration = duration.plus(this.totalDuration);
                duration = duration.plus(Duration.between(this.startTime, now));
            } else {
                duration = this.totalDuration;
            }

            long secs = Math.abs(duration.getSeconds());
            String durationString = String.format(
                "%02d:%02d:%02d",
                secs / 3600,
                (secs % 3600) / 60,
                secs % 60);
            return durationString;
        }

        public String getTaskDetails() {
            String taskDetails = this.name + "," + this.startTime.toString() + ",";
            taskDetails += this.totalDuration.toString() + ","; 
            if (this.isActive()) { taskDetails += ","; }
            else { taskDetails += this.endTime + ","; }
            taskDetails += this.description;
            
            return taskDetails;
        }

        public boolean isActive() {
            return this.endTime == null;
        }

        public void setEndTime() { this.endTime = now; }
        public void setDescription(String d) { this.description = d; }
    }

    public static class InputHandler {
        private String[] inputArgs;
        private List<String> commandArguments = new ArrayList<>();

        private CLIENT_COMMAND command;

        public InputHandler(String[] input) {
            
            this.inputArgs = input;
            this.command = this.validateCommand();
            this.validateCommandArgs();
        }

        private CLIENT_COMMAND validateCommand() {
            if (this.inputArgs.length < 1 || this.inputArgs.length > 3) 
            { 
                return CLIENT_COMMAND.INVALID; 
            } 
            
            try { return CLIENT_COMMAND.valueOf(inputArgs[0].toUpperCase()); } 

            catch (IllegalArgumentException e) { return CLIENT_COMMAND.INVALID; }
        }

        public void validateCommandArgs() {
            
            switch (this.command) {
                case INVALID:
                    break;
                case START:
                    if (this.inputArgs.length != 2) { this.command = CLIENT_COMMAND.INVALID; return; }
                    this.commandArguments.add(inputArgs[1]);
                    break;
                case STOP:
                    if (this.inputArgs.length != 2) { this.command = CLIENT_COMMAND.INVALID; return; }
                    this.commandArguments.add(inputArgs[1]);
                    break;
                case DESCRIBE:
                    if (this.inputArgs.length != 3) { this.command = CLIENT_COMMAND.INVALID; return; }
                    this.commandArguments.add(inputArgs[1]);
                    this.commandArguments.add(inputArgs[2]);
                    break;
                case SUMMARY:
                    if (this.inputArgs.length != 1 && this.inputArgs.length != 2) { this.command = CLIENT_COMMAND.INVALID; return; }
                    if (this.inputArgs.length == 2) { this.commandArguments.add(inputArgs[1]); }
                    break;
                default:
                    break;  
            }
        }

        public ArrayList<String> getCommandArgs() { return new ArrayList<>(this.commandArguments); }

        public void printUsage() {
            System.out.println("""
            Usage:
            \tTM start <task name> |
            \tTM stop <task name> |
            \tTM describe <task name> <task description in quotes> |
            \tTM summary <task name> |
            \tTM summary
            """);
        }

        public CLIENT_COMMAND getCommand() { return this.command; }
    }
}