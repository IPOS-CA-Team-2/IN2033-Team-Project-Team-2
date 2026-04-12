package app;

import integration.IPuStockUpdater;
import service.OnlineSaleService;
import service.StockService;
import service.WholesaleOrderService;

import javax.swing.SwingUtilities;
import java.util.concurrent.CopyOnWriteArrayList;

// static service locator and UI refresh event bus
// initialised once in Main.main() — all UI screens read services from here
// means no screen needs to construct its own services or know about gateways
public class AppContext {

    private static WholesaleOrderService orderService;
    private static OnlineSaleService     onlineSaleService;
    private static IPuStockUpdater       puAdapter;
    private static StockService          stockService;

    // thread-safe listener lists — CopyOnWriteArrayList so CaApiServer
    // can notify from its background thread without locking the EDT
    private static final CopyOnWriteArrayList<Runnable> orderListeners = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<Runnable> stockListeners  = new CopyOnWriteArrayList<>();

    // called once from Main after all services are wired up
    public static void init(WholesaleOrderService orders,
                            OnlineSaleService     online,
                            IPuStockUpdater       pu,
                            StockService          stock) {
        orderService      = orders;
        onlineSaleService = online;
        puAdapter         = pu;
        stockService      = stock;
    }

    public static WholesaleOrderService getOrderService()    { return orderService; }
    public static OnlineSaleService     getOnlineSaleService(){ return onlineSaleService; }
    public static IPuStockUpdater       getPuAdapter()       { return puAdapter; }
    public static StockService          getStockService()    { return stockService; }

    // order refresh — called by CaApiServer when SA pushes a status update
    // runs each registered Runnable on the EDT so Swing tables refresh safely
    public static void addOrderRefreshListener(Runnable r)    { orderListeners.add(r); }
    public static void removeOrderRefreshListener(Runnable r) { orderListeners.remove(r); }

    public static void notifyOrderRefresh() {
        for (Runnable r : orderListeners) SwingUtilities.invokeLater(r);
    }

    // stock refresh — called by CaApiServer when PU pushes an online sale
    public static void addStockRefreshListener(Runnable r)    { stockListeners.add(r); }
    public static void removeStockRefreshListener(Runnable r) { stockListeners.remove(r); }

    public static void notifyStockRefresh() {
        for (Runnable r : stockListeners) SwingUtilities.invokeLater(r);
    }
}
