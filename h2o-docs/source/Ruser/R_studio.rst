

H\ :sub:`2`\ O installation in R Studio
------------------------------------------


These instructions assume you are using R  2.14.0 or later.  

**STEP 1**

The download package containing the  H\ :sub:`2`\ O jar file can be
obtained by visiting H\ :sub:`2`\ O available downloads at 
`http://0xdata.com/downloadtable <http://0xdata.com/downloadtable/>`_.

Choose the version of  H\ :sub:`2`\ O best for you, and unzip the
downloaded H\ :sub:`2`\ O zip file. The most recent promoted build is
reccomended. 

**STEP 2**

Start an instance of H\ :sub:`2`\ O. For help with this see 
:ref:`GettingStartedFromaZipFile`

If users do not start an instance of H\ :sub:`2`\ O, one will be
started automatically for them at localhost: 54321 (see **STEP 4** for
more detail). 

If the instance of H\ :sub:`2`\ O is stopped, the R
program will no longer run, and work done will be lost. 

**STEP 3:**

Follow the instructions for installation on the page from which the
package was downloaded. Those instructions will look similar to the
instructions in the picture below:  

.. image:: buildindex.png
   :width: 100 %   


Note: users may get warnings of the type "Error in
detatch("package:h2o", unload = TRUE): invalid 'name' argument. 
This tells users that there is no  H\ :sub:`2`\ O package to uninstall. These
warnings can safely be ignored.  
  

**STEP 4:**

Once the  H\ :sub:`2`\ O R package has been installed, call the
package, and establish a connection to a running instance of  H\
:sub:`2`\ O. 

If there is no running instance of  H\ :sub:`2`\ O prior to using
the command "h2o.init()",  H\ :sub:`2`\ O in R will start an instance
automatically for the user at localhost:54321, and the user will be
notified. If you would like to connect to an instance at an IP and
port other than localhost:54321, these details must be specified as
arguments in the R call. 


::

  library(h2o)
  localH2O <- h2o.init()


Users who wish to specify a connection
with a server (other than localhost at port 54321) must explicity
state the IP address and port number in the h2o.init call. 
An example is given below, but **do not cut and paste**; users should
specify the IP and port number appropriate to their specific
environment. 

::

  library(h2o)
  localH2O = h2o.init(ip = "192.555.1.123", port = 12345, startH2O = FALSE) 


**STEP 5: Upgrading Packages**

Users may wish to manually upgrade their R packages. For instance, if
you are running the bleeding edge developer build, it’s possible that
the code has changed, but that the revision number has not, in which
case manually upgrading ensures the most current version of not only
the H\ :sub:`2`\ O code, but the corresponding R code as well.

This can be done by returning to STEP 3, and following the commands
through STEP 4.


 






















