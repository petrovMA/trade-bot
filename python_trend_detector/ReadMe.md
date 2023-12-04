## Updating Python Application in Docker

Follow these steps to update your Python application running in a Docker container:

### Step 1: Stop the Running Container
Stop the container that's currently running your application.
```bash
docker stop container_name
```

List of running containers:
```bash
docker container ls
```

Remove image:
```bash
docker image rm <IMAGE ID>
```


### Step 2: Remove the Stopped Container
Remove the stopped container to make way for a new one.
```bash
docker rm container_name
```
Again, replace container_name with the actual name of your container.

### Step 3: Rebuild the Docker Image
Rebuild your Docker image to include the latest code changes.
```bash
docker build -t your_image_name .
```
Replace your_image_name with the name of your Docker image. The . at the end of the command denotes the current directory where your Dockerfile is located.

### Step 4: Run the Container from the Updated Image
Start a new container using the freshly built image.
```bash
docker run -d -p your_port:your_port --name container_name your_image_name
```


