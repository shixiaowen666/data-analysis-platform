build-base:
	mvn clean package -Dmaven.test.skip=true -Dmaven.repo.local=/cache/.m2 --settings /root/.m2/settings.xml

build-image:
	docker build -t ${registry}/${image_name}:${image_tag} .
	docker push ${registry}/${image_name}:${image_tag}

build-image-multiarch:
	docker buildx build -t ${registry}/${image_name}:${image_tag} --platform linux/amd64,linux/arm64 --push .



build: build-base build-image

build-multiarch: build-base build-image-multiarch
