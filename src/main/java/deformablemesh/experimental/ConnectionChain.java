package deformablemesh.experimental;

import deformablemesh.geometry.Connection3D;
import deformablemesh.geometry.Node3D;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ConnectionChain {

    List<ChainLink> links = new ArrayList<>();
    void addEdge(Connection3D con){
        ChainLink next = new ChainLink(con, null, null);
        for(ChainLink link : links){
            if( next.backNode == link.frontNode){
                link.front.add(next);
                next.back.add(link);
            } else if( next.frontNode == link.frontNode ){
                link.front.add(next);
                next.front.add(link);
            }
            if( next.backNode == link.backNode){
                link.back.add(next);
                next.back.add(link);
            } else if( next.frontNode == link.backNode ){
                link.back.add(next);
                next.front.add(link);
            }
        }
        links.add(next);
    }
    List<Connection3D> getList(){
        List<Connection3D> list = new ArrayList<>();
        Map<Boolean, List<ChainLink>> partitions = links.stream().collect(Collectors.partitioningBy(cl -> cl.front.isEmpty() || cl.back.isEmpty()));
        list.addAll(partitions.get(true).stream().map(cl->cl.item).collect(Collectors.toList()));
        list.addAll(partitions.get(false).stream().map(cl->cl.item).collect(Collectors.toList()));
        return list;
    }
    static class ChainLink{
        final Connection3D item;
        List<ChainLink> back = new ArrayList<>();
        List<ChainLink> front = new ArrayList<>();
        final Node3D backNode;
        final Node3D frontNode;
        public ChainLink(Connection3D item, ChainLink prev, ChainLink next){
            this.item = item;
            backNode = item.A;
            frontNode = item.B;
        }
    }

    static public List<List<Connection3D>> chainFourByConnections(List<Connection3D> connections){
        List<List<Connection3D>> chainedConnections = new ArrayList<>();
        for(int i = 0; i<connections.size(); i++){
            Connection3D c0 = connections.get(i);
            boolean connect = false;
            List<Integer> asking = new ArrayList<>();
            for(int chainDex = 0; chainDex<chainedConnections.size(); chainDex++){
                if(chainedConnections.get(chainDex).stream().anyMatch(con->{
                    return con.A == c0.A || con.A == c0.B || con.B == c0.A || con.B == c0.B;
                })){
                    asking.add(chainDex);
                    connect = true;
                }
            }
            if(!connect){
                List<Connection3D> newChain = new ArrayList<>();
                newChain.add(c0);
                chainedConnections.add(newChain);
            } else{
                if(asking.size() == 1){
                    chainedConnections.get(asking.get(0)).add(c0);
                } else{
                    List<Connection3D> newChain = new ArrayList<>();
                    List<List<Connection3D>> oldChains = new ArrayList<>();
                    for(Integer cd : asking){
                        List<Connection3D> joining = chainedConnections.get(cd);
                        newChain.addAll(joining);
                        oldChains.add(joining);
                    }
                    newChain.add(c0);
                    chainedConnections.removeAll(oldChains);
                    chainedConnections.add(newChain);
                }
            }
        }
        System.out.println(chainedConnections);
        List<ConnectionChain> chains = new ArrayList<>();
        for(List<Connection3D> cons : chainedConnections){
            ConnectionChain c = new ConnectionChain();
            for(Connection3D con : cons){
                c.addEdge(con);
            }
            chains.add(c);
        }
        return chains.stream().map(ConnectionChain::getList).collect(Collectors.toList());
    }

}
