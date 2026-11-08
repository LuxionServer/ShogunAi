using System;
using System.Diagnostics;
using System.IO;

public static class FakeRunnerLauncher
{
    public static int Main()
    {
        string executable = Process.GetCurrentProcess().MainModule.FileName;
        string[] launch = File.ReadAllLines(Path.ChangeExtension(executable, ".launch"));
        ProcessStartInfo start = new ProcessStartInfo(launch[0], launch[1] + OwnArguments(Environment.CommandLine));
        start.UseShellExecute = false;
        using (Process process = Process.Start(start))
        {
            process.WaitForExit();
            return process.ExitCode;
        }
    }

    private static string OwnArguments(string commandLine)
    {
        int index = 0;
        bool quoted = false;
        while (index < commandLine.Length && (quoted || (commandLine[index] != ' ' && commandLine[index] != '\t')))
        {
            if (commandLine[index] == '"')
            {
                quoted = !quoted;
            }
            index++;
        }
        return commandLine.Substring(index);
    }
}
