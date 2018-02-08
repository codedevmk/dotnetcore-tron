using System;
using System.Collections;
using System.Collections.Generic;

namespace dotNetCoreTron.Common
{
    public class Args
    {
        private static Args INSTANCE = new Args();

        //TODO: Replace JCommander Parms and jCommand with .NET version
        //@Parameter(names = { "-d", "--outputDirectory"}, description = "Directory")
        private String outputDirectory = String.Empty;

       // @Parameter(names = { "-h", "--help"}, help = true, description = "Directory")
        private Boolean help = false;

       // @Parameter(description = "seedNodes")
        private dynamic seedNodes = new ArrayList();

        public static void setParam(String[] args)
        {
           // JCommander.newBuilder().addObject(INSTANCE).build().parse(args);
        }

        public static Args getInstance()
        {
            return INSTANCE;
        }

        public String getOutputDirectory()
        {
            if (((outputDirectory != "") && !outputDirectory.EndsWith("/")))
            {
                return (outputDirectory + "/");
            }

            return outputDirectory;
        }

        public bool isHelp()
        {
            return help;
        }

        public List<String> getSeedNodes()
        {
            return seedNodes;
        }
    }
}
