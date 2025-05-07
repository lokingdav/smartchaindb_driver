# SmartchainDB Driver

# Initial Project Setup
- To be written...

# Run Project
To run the project, run command below:

```bash
gradle run
```

The command above will run ```com.bigchaindb.smartchaindb.driver.ProcessesRunner``` class which will spawn 1 new process which runs the code in ```com.bigchaindb.smartchaindb.driver.BigchainDBJavaDriver``` class. 

If you want to spawn multiple processes then run:
```bash
gradle run -PnumProcesses=<int>
```
Replace ```<int>``` with the number of processes to run. Usually the number of CPUs or vCPUs on your machine or even more.