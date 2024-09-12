# gridsuite-app-client
Generate for front apps the client to the backend

#PAss arguments
`-J-Darg=val`


# FAQ
## Why are scripts in java and/or node?
These scripts are first means to be used by the CI. However, as we leave to users
the possibility to manually run the scripts, we need to write it in a language
that works for Unix & Windows systems, and without needing to install another runtime.  
So we used JShell for the part working on Java+Maven repositories and NodeJS on webapp client.
