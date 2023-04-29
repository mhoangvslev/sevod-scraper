#!/bin/bash

mode="$1"
n_containers=10

function probe(){
    response_code=$(curl -o /dev/null --silent --head --write-out '%{http_code}\n' $1)
    echo "$response_code"
}

if [ "$mode" = "void" ]; then

for container_id in $(seq 1 $n_containers)
do
    container_name="docker-bsbm-virtuoso-${container_id}"
    echo "Stopping all containers..."
    docker-compose -f ../../../experiments/bsbm/docker/virtuoso.yml stop bsbm-virtuoso

    batch_id=$(expr $container_id - 1)
    summary_file="../semagrow/metadata$batch_id.ttl"

    if [ -f $summary_file ]; then
        echo "$summary_file is already generated, skipping..."
        continue
    else

        echo "Starting $container_name ..."
        docker start $container_name
        container_infos=$(docker ps --all --format '{{.Names}} {{.Ports}}')
        container_port=$(echo "$container_infos" | grep "$container_name " | awk '{print $4}' | sed -E "s#0\.0\.0\.0:([0-9]+).*#\1#g")
        container_endpoint="http://localhost:$container_port/sparql"

        if [ -z "$container_port" ]; then
            echo "Container endpoint not found!" && exit -1
        fi

        attempt=0
        while [ "$(probe $container_endpoint)" != "200" ]; do
            echo "Waiting for $container_endpoint, attempt = $attempt..."
            sleep 1
            attempt=$(expr $attempt + 1)
        done

        echo "Generating summary for endpoint $container_endpoint"
        mvn -q exec:java -pl "cli/" -Dexec.mainClass="org.semagrow.sevod.scraper.cli.Main" -Dexec.args="--sparql --input $container_endpoint --output $summary_file" || exit -1
    fi
done
elif [ "$mode" = "hibiscus" ]; then
    #for nt_file in ../../../experiments/bsbm/model/dataset/*.nt
    for nt_file in ../../../experiments/bsbm/model/batch_dump/*.nt
    do
        batch_id=$(echo "$nt_file" | egrep -o "[0-9]+")
        #summary_file="../../../experiments/bsbm/model/dataset/metadata_$batch_id.ttl"
        summary_file="../../../experiments/bsbm/model/dataset/batch_dump/metadata_$batch_id.ttl"
        echo "$summary_file"
        mvn -q exec:java -pl "cli/" -Dexec.mainClass="org.semagrow.sevod.scraper.cli.Main" -Dexec.args="--rdfdump --input $nt_file --output $summary_file"
    done
else
    echo "Mode must be either 'void' or 'hibiscus', found $mode"
fi