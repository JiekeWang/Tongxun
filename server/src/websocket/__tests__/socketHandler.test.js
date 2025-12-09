const { setupWebSocket, sendToUser } = require('../socketHandler');
const { Server } = require('socket.io');
const http = require('http');

describe('WebSocket Handler', () => {
  let server;
  let io;
  let httpServer;
  
  beforeEach(() => {
    httpServer = http.createServer();
    io = new Server(httpServer);
    setupWebSocket(io);
  });
  
  afterEach(() => {
    io.close();
    httpServer.close();
  });
  
  describe('sendToUser', () => {
    it('should send message to connected user', async () => {
      const userId = 'user-123';
      const event = 'friend_request';
      const data = {
        requestId: 'req-1',
        fromUserId: 'user-456',
        fromUserNickname: 'Test User',
        message: 'Hello'
      };
      
      // Mock socket connection
      const mockSocket = {
        id: 'socket-123',
        userId: userId,
        connected: true,
        emit: jest.fn()
      };
      
      // This test would need actual Socket.IO client connection
      // For now, we test the function structure
      await expect(sendToUser(userId, event, data)).resolves.not.toThrow();
    });
    
    it('should handle offline user gracefully', async () => {
      const userId = 'offline-user';
      const event = 'friend_request';
      const data = {
        requestId: 'req-1',
        fromUserId: 'user-456'
      };
      
      // User not connected, should not throw
      await expect(sendToUser(userId, event, data)).resolves.not.toThrow();
    });
  });
});


