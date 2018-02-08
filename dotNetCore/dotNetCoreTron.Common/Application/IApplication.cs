using System;

namespace dotNetCoreTron.Common
{
    public interface Application
    {

      //  void setOptions(Args args);

      //  void init(String path, Args args);

     //   void initServices(Args args);

        void startup();

        void shutdown();

        void startServices();

        void shutdownServices();

       // Node getP2pNode();

      //  BlockStore getBlockStoreS();

       // void addService(Service service);

       // Manager getDbManager();
    }
}
