package AutoGarcon;

import static spark.Spark.*;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import spark.Request;
import spark.Response;
import java.io.InputStream;
import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException; 
import javax.servlet.http.HttpServletResponse; 


/**
 * Main:  This class contains all of the logic for 
 * API routes and their respective handlers.
 * 
 * 
 * @author  Tyler Beverley, 
 * @author Sosa Edison  
 * @version 0.1
 * @since 2/24/20
 * @see <a href="https://github.com/auto-garcon/documentation/blob/master/APISpecification.md">Documentation</a>;  For API Endpoints. 
 * @see <a href="https://github.com/auto-garcon/backend">README</a>; For build instructions. 
 * 
 * You *can* refer to the documentation 
 * for information about this API, but as always, 
 * the code is the primary source of truth. 
 * Meaning that it's possible for the documentation to be out of date 
 * But the code will always be current. 
 *
 *
 * All of the route handlers will take in two paramaters, a request and a response object. 
 * These are from the Spark Java web framework, and are used to interact with 
 * the response and request sent to the API's endpoints. 
 */
public class Main {


    /**
     * endpointNotImplemented: Default functionality for when an endpoint has not been implemeneted yet. 
     * @param Request - Request object. 
     * @param Response - Response object.  
     */
    public static Object endpointNotImplemented( Request req, Response res ){
        res.status(501); 
        return "Endpoint Not Implemented"; 
    }

    /**
     * serveStatic: Serve the web UI's files. 
     * @param Request - Request object. 
     * @param Response - Response object.  
     */
    public static Object serveStatic(Request req, Response res) {
        res.type("text/html");
        res.redirect("build/index.html", 201);
        return "";
    }

    /**
     * serveImage: serve an image file. 
     * depreciated
     */
    public static Object serveImage( Request req, Response res ){
        res.type("image/webp");
        String menuItemID = req.params(":menuitemid"); 
        res.redirect("images/" + menuItemID + ".jpg", 201);
        return "";
    }


    public static Object getTableInfo( Request req, Response res ){

        int restaurantID = Integer.parseInt(req.params(":restaurantid")); 
        String alexaID = req.queryParamOrDefault("alexaid", ""); 
        int tableNumber = Integer.parseInt(req.queryParamOrDefault("tablenumber", "-1")); 

        if( tableNumber == -1 && alexaID.equals("") ){
            //get all the tables for the restaurant.  
            //Table[] tables = Table.getAllTables( int restaurantID );  
            return "Get all tables not implemented yet";
        }
        else if( !alexaID.equals("") ){
            //get the table coresponding to the alexa.
            Table table = Table.tableFromAlexaID( alexaID );
            res.status(200); 
            return table; 
        }
        else if( tableNumber != -1 ){
            res.status(200); 
            Table table = Table.tableFromTableID( restaurantID, tableNumber);
            return table; 
        }
        else {
            //this case should not be reachable. 
            res.status(500); 
            return "Failed to get a table"; 
        }
    }


    /**
     * addMenu: Handler for /api/restaurant/:restaurantid/menu/add
     * Adds a menu to a database. 
     * @param Request - Request object. 
     * @param Response - Response object.  
     */
    public static Object addMenu( Request req, Response res) {
  
        Menu menu = Menu.menuFromJson( req.body() );   

        //System.out.printf("Printing Incoming menu:\n %s\n", menu.toString());
        boolean saved = menu.save(); 

        if (!menu.isDefault() && saved) {
            res.status(200);
            return "Successfully recieved menu.";
        } else {
            res.status(500); 
            return "Error recieving menu"; 
        }
    }

    /**
     * initializeOrder: Handler for api/restaurant/:restaurantid/:tablenumber/order/new
     * Populates order object with initial fields
     * @param Request - Request object. 
     * @param Response - Response object.  
     */
    public static Object initializeOrder( Request req, Response res) {

        Order order = Order.orderFromJson( req.body() );
        boolean initialized = order.initializeOrder(order); 
        int restaurantID = Integer.parseInt(req.params(":restaurantid")); 
        int tableNumber = Integer.parseInt(req.params(":tablenumber")); 
        int customerID = order.getCustomerID(); 

        //set the tableID and customer ID to make the createOrder database call later
        int tableID = DBUtil.getTableID(restaurantID, tableNumber);
        if(tableID < 0) {
            return "Invalid restaurant ID and table number combination";
        }
        order.setTableID(tableID);
        order.setCustomerID( customerID ); 

        OrderTracker tracker = OrderTracker.getInstance();
        tracker.addOrder(restaurantID, tableNumber, order ); 

        if (!order.isDefault() && initialized) {

            JSONObject resp = new JSONObject(); 
            resp.put("customerID", Integer.toString(customerID));
            resp.put("restaurantID", Integer.toString(restaurantID)); 
            resp.put("tableNumber", Integer.toString(tableNumber)); 

            res.status(200);
            return res.toString();
        } else {
            res.status(500); 
            return "Error recieving menu"; 
        }
    }

    /**
     * addItemToOrder: Handler for api/restaurant/:restaurantid/:tablenumber/order/add
     * Adds item to existing order at the specified table
     * @param Request - Request object. 
     * @param Response - Response object.  
     */
    public static Object addItemToOrder( Request req, Response res) {
  
        OrderItem orderItem = OrderItem.orderItemFromJson( req.body() );
        int restaurantID = Integer.parseInt(req.params(":restaurantid")); 
        int tableNumber = Integer.parseInt(req.params(":tablenumber")); 

        OrderTracker tracker = OrderTracker.getInstance();
        Order order = tracker.getOrder( restaurantID, tableNumber ); 

        if( order == null ){
            System.out.printf("Tried to add an item to a non-existant order.\n" +
                    "restaurantID: %d, tableNumber: %d.\n", 
                    restaurantID, tableNumber 
            );
            res.status(400); 
            return "No open order for this table."; 
        }

        order.addOrderItem( orderItem ); 
        res.status(200);
        return "Successfully added item to order.";
    }

    /**
     * submitOrder: Handler for api/restaurant/:restaurantid/:tablenumber/order/submit
     * Submits the order once it is all built
     * @param Request - Request object. 
     * @param Response - Response object.  
     */
    public static Object submitOrder( Request req, Response res) {
        int restaurantID = Integer.parseInt(req.params(":restaurantid")); 
        int tableNumber = Integer.parseInt(req.params(":tablenumber")); 

        OrderTracker tracker = OrderTracker.getInstance();
        Order order = tracker.getOrder( restaurantID, tableNumber );

        if( order == null ){
            System.out.printf("Tried to add an item to a non-existant order.\n" +
                    "restaurantID: %d, tableNumber: %d.\n", 
                    restaurantID, tableNumber 
            );
            res.status(400); 
            return "No open order for this table."; 
        }
        if( !order.isDefault() ){
            boolean saved = order.save(); 
            if( saved ){
                tracker.completeOrder(restaurantID, tableNumber);
                res.status(200); 
                return "Successfully saved your order"; 
            }
            else { 
                res.status(500); 
                return "Failed to save your order"; 
            }
        }
        else {
            res.status(400); 
            return "Failed to parse out request for submitting a order"; 
        }

    }

    /**
     * getOrdersWithin24Hours: Handler for api/users/:userid/orders
     * Gets all orders for the user within 24 hours
     * @param Request - Request object. 
     * @param Response - Response object.  
     */
    public static Object getOrdersWithin24Hours( Request req, Response res) {
        try{ 
            int userID = Integer.parseInt(req.params(":userid"));
            ArrayList<Order> result = Order.allOrders(userID); 
            if(result.size() > 0){
                res.status(200); 
                return result;
            } else {
                res.status(500); 
                return "Cannot find any orders for this user";
            }
        } catch( NumberFormatException nfe ){
            res.status(400); 
            return "Failed to get results from getOrdersWithin24Hours."; 
        }
    }

    /**
     * sitDownAndGetTableID: Handler for api/restaurant/:restaurantid/:tablenumber/user/:userid/sitdown
     * Gets the table ID from the query string's restaurantID and table number
     * @param Request - Request object. 
     * @param Response - Response object.  
     */
    public static Object sitDownAndGetTableID( Request req, Response res) {

        try{ 
            int userID = Integer.parseInt(req.params(":userid"));
            int restaurantID = Integer.parseInt(req.params(":restaurantID"));
            int tableNumber = Integer.parseInt(req.params(":tablenumber"));
            int tableID = DBUtil.getTableID(restaurantID, tableNumber);

            //attach this user to the table
            UserTracker tracker = UserTracker.getInstance();
            tracker.addUser(restaurantID, tableNumber, userID);


            if(tableID >= 0){
                res.status(200);
                //return the tableID in JSON form
                HashMap<String,Integer> jsonMap = new HashMap<>();
                jsonMap.put("tableID", tableID);
                return new JSONObject(jsonMap);
            } else {
                res.status(500); 
                return "Invalid restaurant ID and table number combination";
            }
        } catch( NumberFormatException nfe ){
            res.status(400); 
            return "Failed to parse restaurantID and tableNumber as integers."; 
        }
    }

    /**
     * markOrderReady: Handler for api/restaurant/:restaurantid/orders/:orderid/complete
     * Marks an order ready to go out to a table
     * @param Request - Request object. 
     * @param Response - Response object.  
     */
    public static Object markOrderReady( Request req, Response res) {
        try{ 
            int orderID = Integer.parseInt(req.params(":orderid"));
            boolean success = DBUtil.markOrderReady(orderID);
            if( success ){
                res.status(200); 
                return "Successfully marked order ready"; 
            }
            else { 
                res.status(500); 
                return "Failed to mark order ready"; 
            }
        } catch( NumberFormatException nfe ){
            res.status(400); 
            return "Failed to parse order ID in markOrderReady."; 
        }
    }

    /**
     * addFavoriteRestaurant: Handler for api/users/:userid/favorites/restaurant/:restaurantid/add
     * Marks an order ready to go out to a table
     * @param Request - Request object. 
     * @param Response - Response object.  
     */
    public static Object addFavoriteRestaurant( Request req, Response res) {
        try{ 
            int userID = Integer.parseInt(req.params(":userid"));
            int restaurantID = Integer.parseInt(req.params(":restaurantid"));
            boolean success = DBUtil.addFavoriteRestaurant(userID, restaurantID);
            if( success ){
                res.status(200); 
                return "Successfully added favorite restaurant"; 
            }
            else { 
                res.status(500); 
                return "Failed to add favorite restaurant"; 
            }
        } catch( NumberFormatException nfe ){
            res.status(400); 
            return "Failed to parse user and restaurant IDs in addFavoriteRestaurant."; 
        }
    }

    /**
     * removeFavoriteRestaurant: Handler for api/users/:userid/favorites/restaurant/:restaurantid/remove
     * Marks an order ready to go out to a table
     * @param Request - Request object. 
     * @param Response - Response object.  
     */
    public static Object removeFavoriteRestaurant( Request req, Response res) {
        try{ 
            int userID = Integer.parseInt(req.params(":userid"));
            int restaurantID = Integer.parseInt(req.params(":restaurantid"));
            boolean success = DBUtil.removeFavoriteRestaurant(userID, restaurantID);
            if( success ){
                res.status(200); 
                return "Successfully removed favorite restaurant"; 
            }
            else { 
                res.status(500); 
                return "Failed to remove favorite restaurant"; 
            }
        } catch( NumberFormatException nfe ){
            res.status(400); 
            return "Failed to parse user and restaurant IDs in removeFavoriteRestaurant."; 
        }
    }

     /**
     * removeMenu: Handler for api/restaurant/:restaurantid/menu/:menuid/remove
     * Removes a menu from the database (marks it inactive)
     * @param Request - Request object. 
     * @param Response - Response object.  
     */
    public static Object removeMenu( Request req, Response res) {
        try{ 
            int menuID = Integer.parseInt(req.params(":menuid"));
            boolean success = DBUtil.removeMenu(menuID);
            if( success ){
                res.status(200); 
                return "Successfully removed menu"; 
            }
            else { 
                res.status(500); 
                return "Failed to remove menu"; 
            }
        } catch( NumberFormatException nfe ){
            res.status(400); 
            return "Failed to parse menu and restaurant IDs in removeMenu."; 
        }
    }

    /**
     * removeMenuItem: Handler for api/restaurant/:restaurantid/menu/:menuid/item/:itemid/removefromall
     * Removes a menu from the database (marks it inactive)
     * @param Request - Request object. 
     * @param Response - Response object.  
     */
    public static Object removeMenuItem( Request req, Response res) {
        try{ 
            int itemID = Integer.parseInt(req.params(":itemid"));
            boolean success = DBUtil.removeMenuItem(itemID);
            if( success ){
                res.status(200); 
                return "Successfully removed menu item"; 
            }
            else { 
                res.status(500); 
                return "Failed to remove menu item"; 
            }
        } catch( NumberFormatException nfe ){
            res.status(400); 
            return "Failed to parse menu and restaurant IDs in removeMenuItem."; 
        }
    }

    /**
     * removeMenuItem: Handler for api/restaurant/:restaurantid/menu/:menuid/item/:itemid/remove
     * Removes a menu from the specified menu
     * @param Request - Request object. 
     * @param Response - Response object.  
     */
    public static Object removeMenuItemFromMenu( Request req, Response res) {
        try{ 
            int itemID = Integer.parseInt(req.params(":itemid"));
            int menuID = Integer.parseInt(req.params(":menuid"));
            boolean success = DBUtil.removeItemFromMenu(itemID, menuID);
            if( success ){
                res.status(200); 
                return "Successfully removed menu item"; 
            }
            else { 
                res.status(500); 
                return "Failed to remove menu item"; 
            }
        } catch( NumberFormatException nfe ){
            res.status(400); 
            return "Failed to parse menu and restaurant IDs in removeMenuItemFromMenu."; 
        }
    }

    /**
     * getFavoriteRestaurants: Handler for api/users/:userid/favorites/
     * Gets all the favorite restaurants for a user
     * @param Request - Request object. 
     * @param Response - Response object.  
     */
    public static Object getFavoriteRestaurants( Request req, Response res) {
        try{ 
            int userID = Integer.parseInt(req.params(":userid"));
            ArrayList<FavoriteRestaurant> result = FavoriteRestaurant.allFavorites(userID);
            if( result.size() > 0 ){
                res.status(200); 
                return result; 
            } else { 
                res.status(500); 
                return "Cannot get any favorite restaurants for the user"; 
            }
        } catch( NumberFormatException nfe ){
            res.status(400); 
            return "Failed to parse userID in getFavoriteRestaurants."; 
        }
    }

    /**
     * getAllMenu: Handler for /api/restaurant/:restaurantid/menu/
     * gets all the menus for the specified restaurant.  
     * @param Request - Request object. 
     * @param Response - Response object.  
     */
    public static Object getAllMenu( Request req, Response res ){

        try{ 
            int restaurantID = Integer.parseInt(req.params(":restaurantid")); 
            res.status(200); 
            return Menu.allMenus( restaurantID ); 
        } catch( NumberFormatException nfe){
            res.status(400); 
            return "Failed to parse restaurantID as an integer."; 
        }
    }

    /**
     * getAllRestaurants: Handler for /api/restaurant/
     * gets all the restaurants, can be used to select a random one
     * @param Request - Request object. 
     * @param Response - Response object.  
     */
    public static Object getAllRestaurants( Request req, Response res ){
        res.status(200); 
        ResultSet result = DBUtil.getAllRestaurants();
        if(result == null){
            res.status(400);
            return "Failed to get all restaurants";
        }
        try{
            JSONArray jsonResult = ResultSetConverter.convert(result);
            return jsonResult;
        } catch( SQLException se ){
            res.status(500);
            System.out.printf("SQL Exception while converting all restaurants to correct format.\n" + 
                "Exception: %s\n", se.toString() );
            return "Exception while converting all restaurants to correct format.";
        }
    }

    /**
     * addUser: When a user is signing in, if they are not 
     * already added to the database, add them to the database.
     * @param User - User object. 
     * @param Response - Response object.  
     */
    public static Object addUser(User user, Response res ) {

        boolean saved = user.save(); 

        if( !user.isDefault() && saved ){
            res.status(200); 
            return user.getUserID();  
        } else {
            res.status(500); 
            return "Error receiving User"; 
        }
    }

    /**
     * signIn: Handler for /api/users/signin 
     * Check if the user is in hte database, and give back the userID. 
     * @param Request - Request object. 
     * @param Response - Response object.  
     */
    public static Object signIn( Request req, Response res ){

        User user = User.userFromJson( req.body() );
        int userID = DBUtil.getUserID( user ); 
        //System.out.println( user.toString() ); 

        if(userID == -1 ){
            return addUser( user, res ); 
        }
        else {
            res.status(200); 
        }
        return userID; 
    }

    /**
     * getRestaurant: Handler for /api/restaurant/:restaurantid  
     * gets info about the specifed restaurantID.
     * @param Request - Request object. 
     * @param Response - Response object.  
     */
    public static Object getRestaurant( Request req, Response res ){

        try {
            int restaurantID = Integer.parseInt(req.params(":restaurantid")); 
            Restaurant restaurant = new Restaurant( restaurantID );  
            return restaurant; 

        } catch( NumberFormatException nfe){
            res.status(400); 
            return "Failed to parse restaurantID as an integer.";
        }
    }

    /**
     * addRestaurant: Handler for /api/restaurant/add
     * adds restaurant info to the databse.
     * @param Request - Request object. 
     * @param Response - Response object.  
     */
    public static Object addRestaurant( Request req, Response res ){
        Restaurant restaurant = Restaurant.restaurantFromJson( req.body() ); 

        if( !restaurant.isDefault() ){
            boolean saved = restaurant.save(); 
            if( saved ){
                res.status(200); 
                return "Successfully saved new restuarant"; 
            }
            else { 
                res.status(500); 
                return "Failed to save new restaurant"; 
            }
        }
        else {
            res.status(400); 
            return "Failed to parse out request for adding a restaurant"; 
        }
    }

    /**
     * submitCompleteOrder: Handler for api/restaurant/:restaurantid/order/submit
     * adds a full order to the database
     * @param Request - Request object. 
     * @param Response - Response object.  
     */
    public static Object submitCompleteOrder( Request req, Response res ){
        Order order = Order.orderFromJson( req.body() ); 

        if( !order.isDefault() ){
            boolean saved = order.save(); 
            if( saved ){
                res.status(200); 
                return "Successfully saved new order"; 
            }
            else { 
                res.status(500); 
                return "Failed to save new order"; 
            }
        }
        else {
            res.status(400); 
            return "Failed to parse out request for submitting a order"; 
        }
    }

    /**
     * getOrdersForRestaurant: Handler for api/restaurant/:restaurantid/order/
     * adds a full order to the database
     * @param Request - Request object. 
     * @param Response - Response object.  
     */
    public static Object getOrdersForRestaurant( Request req, Response res ){
        try{ 
            int restaurantID = Integer.parseInt(req.params(":restaurantid"));
            ArrayList<Order> result = Order.allOrdersForRestaurant(restaurantID); 
            if(result.size() > 0){
                res.status(200); 
                return result;
            } else {
                res.status(500); 
                return "Cannot find any orders for this restaurant";
            }
        } catch( NumberFormatException nfe ){
            res.status(400); 
            return "Failed to parse restaurantID in getOrdersForRestaurant."; 
        }
    }

    /**
     * getImage: Handler for /api/images/:menuid/:menuitemid 
     * gets the image associated with the specifed menuitem. 
     * @param Request - Request object. 
     * @param Response - Response object.  
     *
     * This function writes the image bytes to the response's outputstream.
     */
    public static Object getImage( Request req, Response res ){
        
        res.raw().setContentType("image/png");
        byte[] data = null;

        try{
            int menuID = Integer.parseInt( req.params(":menuid") ); 
            int menuItemID = Integer.parseInt( req.params(":menuitemid") ); 

            HttpServletResponse raw = res.raw();
            res.header("Content-Disposition", "attachment; filename=image.jpg");
            //res.type("application/force-download");

            File f = ImageUtil.getImage( menuID, menuItemID );
            data = Files.readAllBytes( f.toPath() );
            raw.getOutputStream().write(data);
            raw.getOutputStream().flush();
            raw.getOutputStream().close();
            res.status(200); 
            return raw; 

        } catch( NumberFormatException nfe ){
            res.status(400); 
            return "Failed to parse paramaters an integer."; 
        }
        catch( IOException ioe ){
            res.status(500); 
            System.out.printf("Failed to retreive the requested image. " + 
                    "Exception: %s\n", ioe.toString()
            ); 
            return "IOException occured";
        }
    }

    /**
     * saveImage: Handler for /api/images/:menuid/:menuitemid (POST)
     * Saves an image to the server's file system given an image upload. 
     * @param Request - Request object. 
     * @param Response - Response object.  
     *
     * This function reads from the request's inputstream.
     */
    public static Object saveImage( Request req, Response res ){

        try{
            int menuID = Integer.parseInt( req.params(":menuid") ); 
            int menuItemID = Integer.parseInt( req.params(":menuitemid") ); 
            InputStream is = req.raw().getPart("uploaded_file").getInputStream();
            req.attribute("org.eclipse.jetty.multipartConfig", new MultipartConfigElement("/temp"));
            boolean saved = ImageUtil.saveImage( menuID, menuItemID, is ); 
            if( saved ){
                return "File uploaded";
            }
            else{
                return "Failed to upload the image"; 
            }
        }
        catch( IOException ioe ){
            res.status(400); 
            System.out.printf("Failed to get the input stream from the request.\n" + 
                    "Exception: %s\n", ioe.toString()
            );
            return "Failed to get the input stream for the request.";
        }
        catch( ServletException se ){
            res.status(400); 
            System.out.printf("ServletException ? " + 
                    "Exception: %s\n", se.toString() 
            ); 
            return "Failed to get the multipart request config.";  
        }
        catch( NumberFormatException nfe ){
            res.status(400); 
            return "Failed to parse paramaters an integer."; 
        }
    }

    /**
     * initRouter: specifes all of the routes for the API. 
     */
    public static void initRouter(){

		get("/", Main::serveStatic);

        path("/api", () -> {
            path("/users", () -> {
                post("/signin", "application/json", Main::signIn, new JsonTransformer() );
                path("/:userid", () -> {
                    get("", Main::endpointNotImplemented );
                    get("/orders", Main::getOrdersWithin24Hours, new JsonTransformer());
                    path("/favorites", () -> {
                        get("", Main::getFavoriteRestaurants, new JsonTransformer() );
                        path("/restaurant", () -> {
                            path("/:restaurantid", () -> {
                                post("/add", Main::addFavoriteRestaurant, new JsonTransformer() );
                                post("/remove", Main::removeFavoriteRestaurant, new JsonTransformer() );
                            });
                        });
                        
                    });
                });
            });
            path("/restaurant", () -> {
                get("", Main::getAllRestaurants, new JsonTransformer() ); 
                post("/add", Main::addRestaurant, new JsonTransformer() ); 
                path("/:restaurantid", () -> {
                    get("", Main::getRestaurant, new JsonTransformer()); 
                    path("/menu", () -> {
                        get("", Main::getAllMenu, new JsonTransformer() ); 
                        post("/add", "application/json", Main::addMenu, new JsonTransformer()); 
                        path("/:menuid", () -> {
                            post("/remove", Main::removeMenu, new JsonTransformer()); 
                            path("/item", () -> {
                                path("/:itemid", () -> {
                                    post("/removefromall", Main::removeMenuItem, new JsonTransformer()); 
                                    post("/remove", Main::removeMenuItemFromMenu, new JsonTransformer());
                                });
                            });
                        });
                    });
                    path("/tables", () -> {
                        get("", Main::getTableInfo, new JsonTransformer()); 
                        path("/:tablenumber", () -> {
                            path("/order", () -> {
                                post("/new", Main::initializeOrder, new JsonTransformer());
                                post("/add", Main::addItemToOrder, new JsonTransformer());
                                post("/submit", Main::submitOrder, new JsonTransformer());
                            });
                            path("/users", () -> {
                                path("/:userid", () -> {
                                    get("/sitdown",Main::sitDownAndGetTableID, new JsonTransformer()); 
                                });
                            });
                        });
                    });
                    path("/order", () -> {
                        get("", Main::getOrdersForRestaurant, new JsonTransformer() ); 
                        post("/submit", Main::submitCompleteOrder, new JsonTransformer());
                        path("/:orderid", () -> {
                            //get("", Main::getOrderByID, new JsonTransformer() ); 
                            post("/complete", Main::markOrderReady, new JsonTransformer());
                        });
                    });
                });
            });
        });
    }


    /**
     * startServer: Start the server on the specified port (443 for https).
     */
    public static void startServer() {

        port(8000);
        // port(443); // HTTPS port
		staticFiles.location("public");

        //secure("/home/ubuntu/env/keystore.jks","autogarcon", null, null); // HTTPS key configuration for spark
        initRouter(); 
        DBUtil.connectToDB();
    }

	public static void main(String[] args) {
        startServer(); 
	}
}



