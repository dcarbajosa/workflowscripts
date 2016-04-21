node{
	sh "rm -rf *"
	stage "test"
	//sendNotification("Approval pending: Job ${env.JOB_NAME} (${env.BUILD_NUMBER})","dcarbajosa@gmail.com","","","","Please go to ${env.BUILD_URL}.")
	//approvalToProceed("do you want to proceed?","","")
	//approvalToProceed("approve it?","Approve","dcarbajosa")
	getSourceCode("master","cdframework",true,"https://github.com/dcarbajosa/cdframework","");
	buildAndUnitTest("./gradlew","clean build","${pwd()}/cdframework","1.0.999999-SNAPSHOT");
	deployWithCredentials("f4b764b9-815b-43c9-bc18-82307bd3f820","./gradlew","uploadArchives","${pwd()}/cdframework","com.cdframework","cdframework","1.0.999999-SNAPSHOT","http://localhost:8081/nexus/content/repositories/snapshots");
	//tagSnapshotWithCredentials("de3ada30-d750-4fde-9a29-278f301f5352", "https://github.com/dcarbajosa/cdframework", "1.0.999999", "this is a test from Jenkins");
	archiveResultAs("ArtifactArchiver","**/cdframework/build/libs/*.jar",true)
	storeFolderInArtifactRepository("f4b764b9-815b-43c9-bc18-82307bd3f820","${pwd()}/cdframework/build/test-results/","http://localhost:8081/nexus/content/repositories/snapshots/com/cdframework/cdframework/1.0.999999-SNAPSHOT/")
   
}
def storeArtifactsInArtifactRepository(credentialsId,fromLocation,filename,url){

	withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId:"${credentialsId}",
			usernameVariable: 'nexusUser', passwordVariable: 'nexusPassword']]) {
		def location="";
		if (fromLocation?.trim ()){
			location= "cd '${fromLocation}';";
		}
		sh "${location}curl -v -k -u ${env.nexusUser}:${env.nexusPassword} --upload-file ${filename} ${url}";
	}
}
def storeFolderInArtifactRepository(credentialsId,folderLocation,url){
	withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId:"${credentialsId}",
			usernameVariable: 'nexusUser', passwordVariable: 'nexusPassword']]) {
		def directory = new File ("${folderLocation}");
		if(directory.isDirectory() && directory.listFiles()!=null && directory.listFiles().length>0){
			processDirectory(directory,directory.getName(),env.nexusUser,env.nexusPassword,url);
		}
	}
}
def processDirectory(dir,parentPath,user,pass,url){
	for(int i=0; i<dir.listFiles().length; i++) {
		if(dir.listFiles()[i].isDirectory()) {
			def path = "${parentPath}/${dir.listFiles()[i].getName()}"
			processDirectory(dir.listFiles()[i],path,user,pass,url)
		}
		else {
			processFile(dir,dir.listFiles()[i], parentPath,user,pass,url)
		}
	}
}
def processFile(dir,file,parentPath,user,pass,url){
	sh "cd ${dir.getPath()}; curl -v -k -u ${user}:${pass} --upload-file ${file.getName()} ${url}${parentPath}/ "
}
def archiveResultAs(archiveWorker,archiveResultsAs,isFingerprint){
	echo "archiving: ${archiveResultsAs}"
	if(archiveWorker.equals("ArtifactArchiver")){
		step([$class: 'ArtifactArchiver', artifacts: archiveResultsAs, fingerprint: isFingerprint]);
	}
	else if(archiveWorker.equals("JUnitResultArchiver")){
		step([$class: 'JUnitResultArchiver', testResults: archiveResultsAs, fingerprint: isFingerprint]);
	}
}
def buildAndUnitTest(buildercommand,action,builderPOMLocation,version){
	if(action?.trim ()){
		def location="";
		if (!buildercommand?.trim ()){
			buildercommand="gradle"
		}
		if (builderPOMLocation?.trim ()){
			location= "cd '${builderPOMLocation}';";
		}
		sh "${location}${buildercommand} ${action} -PartifactVersion=${version}";
	}
	else{
		throw new Exception("GradleScriptWorker.buildAndUnitTest - invalid action!");
	}
}
def deployWithCredentials(credentialsId,buildercommand,action,builderPOMLocation, artifactGroupId, artifactId, artifactVersion, artifactRepoURL){
	if(action?.trim ()){
		withCredentials([[$class:'UsernamePasswordMultiBinding', credentialsId:"${credentialsId}", usernameVariable:'nexusUser', passwordVariable:'nexusPassword']]){
			def location="";
			if (!buildercommand?.trim ()){
				buildercommand="gradle"
			}
			if (builderPOMLocation?.trim ()){
				location= "cd '${builderPOMLocation}';";
			}
			sh "${location}${buildercommand} ${action} -PartifactVersion=${artifactVersion} -PgroupId=${artifactGroupId} -PartifactId=${artifactId} -PnexusUrl=${artifactRepoURL} -PnexusUserName=${env.nexusUser} -PnexusPasswd=${env.nexusPassword}";
		}
	}
	else{
		throw new Exception("GradleScriptWorker.deployWithCredentials - invalid action!");
	}
}
def tagSnapshotWithCredentials(credentialsId,repoUrl,tag,comment){
	withCredentials([[$class:'UsernamePasswordMultiBinding',credentialsId:"${credentialsId}",
		usernameVariable:'gitUser',passwordVariable:'gitPassword']]) {
		if (repoUrl.startsWith("https")){
			repoUrl = "https://${env.gitUser}:${env.gitPassword}@" + repoUrl.replace("https://", "");
		}
		else{
			throw new Exception("tagSnapshotWithCredentials - invalid Url!")
		}
		sh "git remote set-url origin '${repoUrl}'";
		sh "git tag -a ${tag} -m '${comment}'";
		sh "git push origin --tags";
	}
		
}

def getSourceCode(repoBranch,targetDirectory,cleanBeforeCheckout,repoUrl,credentialsId){
	def extensionsList=getExtensions(targetDirectory,cleanBeforeCheckout);

	checkout([$class:'GitSCM', branches:[[name:"${repoBranch}"]],
		doGenerateSubmoduleConfigurations:false,
		extensions:extensionsList,
		submoduleCfg:[],
		userRemoteConfigs:[[url:"${repoUrl}", credentialsId:"${credentialsId}"]]]);

}
def getExtensions(targetDirectory,cleanBeforeCheckout){
	def extensions=[];
	if(targetDirectory?.trim ()){
		extensions << [$class: "RelativeTargetDirectory", relativeTargetDir: "${targetDirectory}"];
		
	}
	if(cleanBeforeCheckout){
		extensions << [$class: "CleanBeforeCheckout"];
	}
	return extensions;
}

def sendNotification(subject,to,bcc,cc,from,body){
	mail charset: "UTF-8", mimeType: "text/plain", subject: "${subject}", to: "${to}", bcc: "${bcc}", cc: "${cc}", from: "${from}", body: "${body}";
}
def approvalToProceed(message,okbutton,submitter){
	input message: "${message}", ok: "${okbutton}", submitter: "${submitter}";
}