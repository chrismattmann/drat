########  setenv.sh ########
#
# Set project specific configuration in setenv.sh
#
# Example:
# 		- Change filemgr URL to http://locatlhost:1234
#			FILEMGR_URL=http://locatlhost:1234
#
#		- Set custom job directory
#			PROJECT_JOB_DIR=/usr/local/project/data/jobs
#
############################

# The deployment this file belongs to, not one fixed when it was written.
#
# Every service control script sources this by way of env.sh, which has
# already worked out OODT_HOME from the calling script's own location. With
# $HOME/drat/deploy as the only default, a deployment installed anywhere
# else silently drove another install's configuration and jars: the file
# manager came up on 9000 answering for the wrong deployment, and the crawl
# that followed failed on every file it offered. An exported DRAT_HOME still
# wins, for anyone who sets one deliberately.
export DRAT_HOME=${DRAT_HOME:-${OODT_HOME:-$HOME/drat/deploy}}
export OODT_HOST=${OODT_HOST:-localhost}

# Set these two to move the whole deployment off the default ports. Every
# URL below is derived from them, so a second OODT installation on the same
# host does not have to be edited into each line -- and, more to the point,
# DRAT does not index into or delete from a Solr that belongs to something
# else.
export TOMCAT_PORT=${TOMCAT_PORT:-8080}
export SOLR_PORT=${SOLR_PORT:-8983}

export FILEMGR_URL=${FILEMGR_URL:-http://$OODT_HOST:9000}
export WORKFLOW_URL=${WORKFLOW_URL:-http://$OODT_HOST:9001}
export RESMGR_URL=${RESMGR_URL:-http://$OODT_HOST:9002}
export FILEMGR_HOME=$DRAT_HOME/filemgr
export PGE_HOME=$DRAT_HOME/pge
export PCS_HOME=$DRAT_HOME/pcs
export OPSUI_URL=${OPSUI_URL:-http://$OODT_HOST:$TOMCAT_PORT/opsui}
export PROTEUS_URL=${PROTEUS_URL:-http://$OODT_HOST:$TOMCAT_PORT/proteus-services}
export SOLR_URL=${SOLR_URL:-http://$OODT_HOST:$SOLR_PORT/solr}
export FMPROD_HOME=$DRAT_HOME/tomcat/webapps/fmprod/WEB-INF/classes/
export SOLR_DRAT_URL=${SOLR_DRAT_URL:-$SOLR_URL/drat}
export DRAT_EXCLUDE=""

#####  Copy and Paste this Block into the .bashrc of your deployment user account ##########
#
# The following aliases must be used within a filemgr installation's
# bin directory since relative pathing is being used.  This block also
# assumes that the filemgr is running on port 9000 (the default port of filemgr)
#
alias fmquery="java -Dorg.apache.oodt.cas.filemgr.properties=$FILEMGR_HOME/etc/filemgr.properties -cp \"$FILEMGR_HOME/lib/*\" org.apache.oodt.cas.filemgr.tools.QueryTool --url $FILEMGR_URL --lucene -query "
#
alias fmdel="java -Dorg.apache.oodt.cas.filemgr.properties=$FILEMGR_HOME/etc/filemgr.properties -cp \"$FILEMGR_HOME/lib/*\" org.apache.oodt.cas.filemgr.tools.DeleteProduct --fileManagerUrl $FILEMGR_URL --read"
#
alias metdump="java -cp \"$FILEMGR_HOME/lib/*\" org.apache.oodt.cas.filemgr.tools.MetadataDumper --url $FILEMGR_URL --out . --productId "
#
######## END OF BLOCK #######
